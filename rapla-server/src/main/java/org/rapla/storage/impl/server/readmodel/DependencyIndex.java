package org.rapla.storage.impl.server.readmodel;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index that precomputes the {@code getDependent} expansion of the
 * read path so it becomes an O(1) lookup instead of a per-query graph walk
 * (PRD 082 — "the getDependent expansion, made O(1)").
 *
 * <p>The read path expands an allocatable to all allocatables that are
 * <em>transitively dependent</em> on it via the belongs-to / packages
 * relations. The authoritative implementation lives in
 * {@code AbstractCachableOperator.getDependent(Collection&lt;Allocatable&gt;)},
 * which delegates to {@code LocalCache.getDependent(...)} → {@code fillDependent}.
 * This index replicates the <em>exact same</em> directed graph and traversal:
 *
 * <h3>The graph (mirrors {@code LocalCache.updateDependencies / addConnection})</h3>
 * Every allocatable is a node. For an allocatable {@code X} of a dynamic type:
 * <ul>
 *   <li>If the type has a belongs-to attribute and {@code X} sets it to target
 *       {@code Y}, an edge {@code X --BelongsTo--&gt; Y} is added, together with
 *       the back-edge {@code Y --BelongsToTarget--&gt; X}.</li>
 *   <li>If the type has a packages attribute and {@code X} sets it to target
 *       {@code Y}, an edge {@code X --Packages--&gt; Y} is added, together with
 *       the back-edge {@code Y --PackagesTarget--&gt; X}.</li>
 * </ul>
 * The attribute values are read via the same unresolved-string path the cache
 * uses ({@code ClassificationImpl.getValuesUnresolvedStrings}), so an edge is
 * recorded even when the target allocatable is not (yet) resolvable — identical
 * to the cache.
 *
 * <h3>The traversal (mirrors {@code LocalCache.fillDependent})</h3>
 * {@code expand(X)} returns {@code X} itself plus every node reachable under the
 * cache's {@code goDown} rule:
 * <ul>
 *   <li>start at {@code X} with {@code goDown = true};</li>
 *   <li>follow a {@code Packages} or {@code BelongsToTarget} edge only while
 *       {@code goDown} is true, continuing with {@code goDown = true};</li>
 *   <li>follow a {@code PackagesTarget} or {@code BelongsTo} edge always,
 *       continuing with {@code goDown = false}.</li>
 * </ul>
 * If a requested id has no node, the id itself is returned (again matching the
 * cache's {@code fillDependent} fallback). The result is a {@link LinkedHashSet}
 * preserving first-seen order, exactly like the cache.
 *
 * <h3>O(1) lookup</h3>
 * The transitive dependent set of each node is computed once and memoized in
 * {@link #closureCache}. A lookup is then a single map read. Any structural
 * change (a {@link #put} or {@link #remove} that alters edges) clears the
 * closure cache, so the next lookup recomputes lazily. Edge maintenance itself
 * is local to the touched allocatable and its targets.
 *
 * <h3>Maintenance hook</h3>
 * Call {@link #put(Allocatable)} whenever an allocatable is stored/updated and
 * {@link #remove(ReferenceInfo)} whenever one is removed — the same lifecycle
 * points at which {@code LocalCache.put} / {@code removeWithId} run
 * {@code updateDependencies}. {@code put} first strips the node's old outgoing
 * edges (and their back-edges on the targets), then re-derives outgoing edges
 * from the current classification — mirroring
 * {@code updateDependencies}'s {@code removeConnections(onlyOutgoing=true)}
 * followed by {@code addConnection}.
 *
 * <p>This class is thread-safe for concurrent reads and serialized writes; the
 * intended caller holds the operator's write lock during {@code put}/{@code remove}.
 */
public final class DependencyIndex
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyIndex.class);

    private static final int MAX_DEPTH = 20;

    enum ConnectionType
    {
        BelongsTo,
        BelongsToTarget,
        Packages,
        PackagesTarget;

        ConnectionType getOpposite()
        {
            switch (this)
            {
                case BelongsTo:       return BelongsToTarget;
                case BelongsToTarget: return BelongsTo;
                case Packages:        return PackagesTarget;
                case PackagesTarget:  return Packages;
            }
            throw new IllegalStateException("ConnectionType not found in case");
        }

        boolean isOutgoing()
        {
            return this == Packages || this == BelongsTo;
        }
    }

    private static final class Node
    {
        final String id;
        /** neighbour-id -> connection type, mirroring {@code GraphNode.connections}. */
        final Map<String, ConnectionType> connections = new ConcurrentHashMap<>();

        Node(String id)
        {
            this.id = id;
        }
    }

    /** allocatable-id -> graph node. */
    private final Map<String, Node> graph = new ConcurrentHashMap<>();

    /** memoized transitive dependent sets; cleared on any structural change. */
    private final Map<String, Set<String>> closureCache = new ConcurrentHashMap<>();

    private Node getOrCreate(String id)
    {
        return graph.computeIfAbsent(id, Node::new);
    }

    /**
     * Re-derive the dependency edges for {@code allocatable} from its current
     * classification. Strips the previous outgoing edges first (and their
     * back-edges on the targets), then adds the current ones. Idempotent.
     *
     * <p>Maintenance hook: call on every store/update of an allocatable.
     */
    public synchronized void put(Allocatable allocatable)
    {
        if (allocatable == null)
        {
            return;
        }
        final String id = allocatable.getReference().getId();
        final Node node = getOrCreate(id);
        // Mirror updateDependencies: remove only this node's OUTGOING edges
        // (and their back-edges on the targets) before re-adding current ones.
        removeOutgoing(node);

        final Object classificationObj = allocatable.getClassification();
        if (classificationObj instanceof ClassificationImpl)
        {
            final ClassificationImpl classification = (ClassificationImpl) classificationObj;
            final DynamicTypeImpl type = classification.getType();
            addConnection(node, classification, type.getBelongsToAttribute(), ConnectionType.BelongsTo);
            addConnection(node, classification, type.getPackagesAttribute(), ConnectionType.Packages);
        }
        else
        {
            LOGGER.debug("Allocatable {} has no ClassificationImpl; no dependency edges derived", id);
        }
        closureCache.clear();
    }

    /**
     * Remove an allocatable from the index, dropping all its edges (outgoing and
     * incoming back-edges). Maintenance hook: call on every remove of an
     * allocatable.
     */
    public synchronized void remove(ReferenceInfo<Allocatable> ref)
    {
        if (ref == null)
        {
            return;
        }
        final String id = ref.getId();
        final Node node = graph.remove(id);
        if (node != null)
        {
            for (String neighbourId : node.connections.keySet())
            {
                final Node neighbour = graph.get(neighbourId);
                if (neighbour != null)
                {
                    neighbour.connections.remove(id);
                }
            }
            node.connections.clear();
        }
        closureCache.clear();
    }

    /** Strip {@code node}'s outgoing edges and the matching back-edges on targets. */
    private void removeOutgoing(Node node)
    {
        for (Map.Entry<String, ConnectionType> entry : node.connections.entrySet())
        {
            if (entry.getValue().isOutgoing())
            {
                final Node target = graph.get(entry.getKey());
                if (target != null)
                {
                    target.connections.remove(node.id);
                }
            }
        }
        node.connections.entrySet().removeIf(e -> e.getValue().isOutgoing());
    }

    private void addConnection(Node node, ClassificationImpl classification, Attribute attribute, ConnectionType sourceType)
    {
        if (attribute == null)
        {
            return;
        }
        final Collection<String> targetIds = classification.getValuesUnresolvedStrings(attribute);
        if (targetIds == null)
        {
            return;
        }
        for (String targetId : targetIds)
        {
            if (targetId != null)
            {
                final Node target = getOrCreate(targetId);
                node.connections.put(target.id, sourceType);
                target.connections.put(node.id, sourceType.getOpposite());
            }
        }
    }

    /**
     * O(1)-amortised equivalent of
     * {@code AbstractCachableOperator.getDependent(...)} at the id level:
     * the union over {@code allocatableIds} of each id's transitive dependent
     * set. The returned set preserves first-seen order and always contains the
     * requested ids themselves.
     */
    public Set<String> expand(Collection<String> allocatableIds)
    {
        final Set<String> result = new LinkedHashSet<>();
        if (allocatableIds == null)
        {
            return result;
        }
        for (String id : allocatableIds)
        {
            if (id != null)
            {
                result.addAll(dependentOf(id));
            }
        }
        return result;
    }

    /** Convenience single-id expansion. */
    public Set<String> expand(String allocatableId)
    {
        return new LinkedHashSet<>(dependentOf(allocatableId));
    }

    /** Memoized transitive dependent set for one id (mirrors {@code fillDependent}). */
    private Set<String> dependentOf(String id)
    {
        Set<String> cached = closureCache.get(id);
        if (cached != null)
        {
            return cached;
        }
        final Set<String> out = new LinkedHashSet<>();
        final Node node = graph.get(id);
        if (node == null)
        {
            out.add(id);
        }
        else
        {
            fill(node, out, 0, true);
        }
        final Set<String> immutable = Collections.unmodifiableSet(out);
        closureCache.put(id, immutable);
        return immutable;
    }

    private void fill(Node node, Set<String> dependents, int depth, boolean goDown)
    {
        if (depth > MAX_DEPTH)
        {
            throw new IllegalStateException("Cycle in dependencies detected");
        }
        if (!dependents.add(node.id))
        {
            return;
        }
        for (Map.Entry<String, ConnectionType> entry : node.connections.entrySet())
        {
            final ConnectionType type = entry.getValue();
            final Node neighbour = graph.get(entry.getKey());
            if (neighbour == null)
            {
                continue;
            }
            if (goDown && (type == ConnectionType.Packages || type == ConnectionType.BelongsToTarget))
            {
                fill(neighbour, dependents, depth + 1, true);
            }
            if (type == ConnectionType.PackagesTarget || type == ConnectionType.BelongsTo)
            {
                fill(neighbour, dependents, depth + 1, false);
            }
        }
    }

    /** Visible for tests: current number of indexed nodes (incl. edge-only target stubs). */
    int nodeCount()
    {
        return graph.size();
    }
}

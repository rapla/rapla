package org.rapla.storage.impl.server;

import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PRD 058 — one-shot startup migration that renames every DynamicType,
 * Attribute, and Category key with a non-GraphQL-spec character to a
 * deterministic spec-compliant key, and propagates the rename through
 * every site that references the old key by name.
 *
 * <p>Lock-acquisition and unlock live in
 * {@link LocalAbstractCachableOperator#migrateGraphqlKeysIfNeeded()} —
 * this class only plans and applies the rewrite, working through the
 * operator's public APIs. All entity edits go through
 * {@link AbstractCachableOperator#editObject(Entity, org.rapla.entities.User)}
 * and {@link AbstractCachableOperator#storeAndRemove(Collection, Collection, org.rapla.entities.User)}.
 *
 * <p>Scope:
 * <ul>
 *   <li>Rename non-spec DT / Attribute / Category keys via the entity {@code setKey}
 *       methods. {@code AttributeImpl.setKey} fires {@code parent.keyChanged} which
 *       updates the DT's parsed-text annotations ({@code nameformat} family) by
 *       re-emitting via {@link org.rapla.entities.dynamictype.internal.DynamicTypeImpl}'s
 *       AttributeFunction — those hold the attribute by immutable ID and resolve the
 *       current key at re-emit time. {@code DynamicTypeImpl.setKey} does the same
 *       for its own annotations.</li>
 *   <li>Adding the edited DT to {@code storeAndRemove} triggers the dispatch path's
 *       {@code addChangedDynamicTypeDependant} which calls {@code commitChange(type)}
 *       on every {@code DynamicTypeDependant} that references the type (reservations,
 *       allocatables, {@code ClassificationFilter} rules in user preferences, etc.).
 *       That walk uses id-based resolution to update each dependent's stored
 *       {@code attributeKey} / classification value maps. No manual annotation or
 *       filter-rule rewrite needed here.</li>
 *   <li>Write a system-preference marker so subsequent boots skip-fast.</li>
 *   <li>{@link #assertCacheSpecCompliant} runs after the migration — fatal error
 *       if any non-spec key remains; server fails to start.</li>
 * </ul>
 */
final class GraphqlKeyMigration
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphqlKeyMigration.class);

    static final TypedComponentRole<String> MARKER_KEY =
            new TypedComponentRole<>("org.rapla.server.graphql-key-migration.applied");

    private GraphqlKeyMigration() {}

    /**
     * True for DynamicTypes added by {@code LocalAbstractCachableOperator.addInternalTypes()} —
     * {@code rapla:period}, {@code rapla:template}, {@code rapla:unresolvedResource},
     * {@code rapla:anonymousEvent}. Production code references them by string
     * constants in {@link org.rapla.entities.storage.EntityResolver} ({@code PERIOD_TYPE},
     * {@code RAPLA_TEMPLATE}, {@code UNRESOLVED_RESOURCE_TYPE}, {@code ANONYMOUSEVENT_TYPE});
     * renaming any of them breaks runtime lookups (e.g. {@code ReferenceHandler.tryResolveMissingAllocatable}
     * which lookups {@code UNRESOLVED_RESOURCE_TYPE} on every dangling reference).
     *
     * <p>Detection is BOTH the annotation check ({@code classification-type=rapla}
     * for PERIOD_TYPE and RAPLA_TEMPLATE) AND the {@code rapla:} key prefix
     * (for UNRESOLVED_RESOURCE_TYPE and ANONYMOUSEVENT_TYPE which are annotated
     * as RESOURCE / RESERVATION but ARE still rapla-internal). Either condition
     * is sufficient — the migration must skip the DT.
     */
    private static boolean isRaplaInternal(DynamicType dt)
    {
        String key = dt.getKey();
        if (key != null && key.startsWith("rapla:")) return true;
        // id keeps the immutable 'rapla:' marker even if the key was already
        // sanitized — never (re)migrate an internal type.
        String id = dt.getId();
        if (id != null && id.startsWith("rapla:")) return true;
        String classKind = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        return DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE.equals(classKind);
    }

    // ---------- public entry points (called from LocalAbstractCachableOperator under its lock) ----------

    static boolean markerSet(AbstractCachableOperator op) throws RaplaException
    {
        Preferences sysPrefs = op.getPreferences(null, false);
        return sysPrefs != null && sysPrefs.getEntryAsString(MARKER_KEY, null) != null;
    }

    /**
     * Plan + apply the migration under the caller's lock. Caller MUST hold the
     * operator's global write lock around this call. After this returns the
     * cache reflects the new keys; the caller should then call
     * {@link #assertCacheSpecCompliant} to verify and write the marker.
     *
     * @return summary string for logging, or empty if nothing changed
     */
    static String runUnderLock(AbstractCachableOperator op) throws RaplaException
    {
        RenamePlan plan = planRenames(op);
        Collection<Entity> toStore = applyPlan(op, plan);
        // marker write — always, even when plan was empty
        writeMarkerInto(op, toStore);
        op.storeAndRemove(toStore, Collections.emptyList(), null);

        if (plan.isEmpty())
        {
            return "no renames; marker written";
        }
        return "renamed " + plan.typeRenames.size() + " DT keys, "
                + plan.attributeRenameCount() + " Attribute keys, "
                + plan.categoryRenames.size() + " Category keys";
    }

    /**
     * Walk the cache after the migration completes (or skip-fasts) and confirm
     * every DT / Attribute / Category key is spec-compliant. Throws if any
     * non-spec key remains — server startup aborts. PRD 058 §6.
     */
    static void assertCacheSpecCompliant(AbstractCachableOperator op) throws RaplaException
    {
        List<String> offenders = new ArrayList<>();
        for (DynamicType dt : op.getDynamicTypes())
        {
            if (dt == null) continue;
            if (isRaplaInternal(dt)) continue;  // rapla-internal DTs use `rapla:*` keys
            if (!Tools.isSpecCompliant(dt.getKey())) offenders.add("DT:" + dt.getKey());
            for (Attribute a : dt.getAttributes())
            {
                if (a == null) continue;
                if (!Tools.isSpecCompliant(a.getKey()))
                {
                    offenders.add("ATTR:" + dt.getKey() + "." + a.getKey());
                }
            }
        }
        Category superCategory = op.getSuperCategory();
        if (superCategory != null)
        {
            walkCategoriesForAssert(superCategory, superCategory, offenders);
        }
        if (!offenders.isEmpty())
        {
            throw new RaplaException(
                    "PRD 058 — operator cache contains " + offenders.size()
                    + " non-GraphQL-spec keys after migration: " + offenders
                    + ". Server refuses to start — investigate why migration didn't catch these.");
        }
    }

    // ---------- marker write ----------

    private static void writeMarkerInto(AbstractCachableOperator op, Collection<Entity> toStore) throws RaplaException
    {
        // If the migration produced a system-prefs edit, reuse it; otherwise
        // edit a fresh copy and add to toStore.
        Preferences sysPrefs = op.getPreferences(null, true);  // create if missing
        PreferencesImpl editable = null;
        for (Entity e : toStore)
        {
            if (e instanceof PreferencesImpl p && p.getId().equals(sysPrefs.getId()))
            {
                editable = p;
                break;
            }
        }
        if (editable == null)
        {
            editable = (PreferencesImpl) op.editObject((Entity) sysPrefs, null);
            toStore.add(editable);
        }
        editable.putEntry(MARKER_KEY, Instant.now().toString());
    }

    // ---------- plan ----------

    static final class RenamePlan
    {
        /** DT-key-old → DT-key-new */
        final Map<String, String> typeRenames = new LinkedHashMap<>();
        /** {DT-key-NEW} → {attribute-key-old → attribute-key-new}.
         *  Keyed by NEW DT key (after rename). */
        final Map<String, Map<String, String>> attributeRenames = new LinkedHashMap<>();
        /** Category-id → new key. Renames are scoped per-parent because category
         *  uniqueness is per-parent, not global. */
        final Map<String, String> categoryRenames = new LinkedHashMap<>();

        boolean isEmpty()
        {
            return typeRenames.isEmpty() && attributeRenames.isEmpty() && categoryRenames.isEmpty();
        }

        int attributeRenameCount()
        {
            int n = 0;
            for (Map<String, String> attrs : attributeRenames.values()) n += attrs.size();
            return n;
        }
    }

    private static RenamePlan planRenames(AbstractCachableOperator op) throws RaplaException
    {
        RenamePlan plan = new RenamePlan();
        Collection<DynamicType> dynamicTypes = op.getDynamicTypes();

        // 1. Type-key renames — disambiguate against all existing + planned new type keys.
        // Skip rapla-internal types (PERIOD_TYPE, RAPLA_REALM_LOGINNAME, etc.) — these
        // are added by addInternalTypes() with hardcoded `rapla:*` keys that are not
        // GraphQL-spec-compliant and are referenced by string from production code.
        // They never reach the SDL generator either (filtered by VALUE_CLASSIFICATION_TYPE_RAPLATYPE).
        Set<String> takenTypeKeys = new HashSet<>();
        for (DynamicType dt : dynamicTypes)
        {
            if (dt != null) takenTypeKeys.add(dt.getKey());
        }
        for (DynamicType dt : dynamicTypes)
        {
            if (dt == null) continue;
            if (isRaplaInternal(dt)) continue;
            String key = dt.getKey();
            if (Tools.isSpecCompliant(key)) continue;
            String newKey = Tools.toSpecKey(key, takenTypeKeys);
            plan.typeRenames.put(key, newKey);
            takenTypeKeys.add(newKey);
        }

        // 2. Attribute-key renames per DT — unique within a DT, not globally.
        for (DynamicType dt : dynamicTypes)
        {
            if (dt == null) continue;
            if (isRaplaInternal(dt)) continue;
            String dtKeyAfterRename = plan.typeRenames.getOrDefault(dt.getKey(), dt.getKey());
            Set<String> takenAttrKeys = new HashSet<>();
            for (Attribute a : dt.getAttributes()) if (a != null) takenAttrKeys.add(a.getKey());
            Map<String, String> attrMap = null;
            for (Attribute a : dt.getAttributes())
            {
                if (a == null) continue;
                String aKey = a.getKey();
                if (Tools.isSpecCompliant(aKey)) continue;
                String newKey = Tools.toSpecKey(aKey, takenAttrKeys);
                if (attrMap == null) attrMap = new LinkedHashMap<>();
                attrMap.put(aKey, newKey);
                takenAttrKeys.add(newKey);
            }
            if (attrMap != null) plan.attributeRenames.put(dtKeyAfterRename, attrMap);
        }

        // 3. Category-key renames — recursive walk under super. Skip super itself
        // and the user-groups subtree (rapla-internal).
        Category superCategory = op.getSuperCategory();
        if (superCategory != null)
        {
            walkCategoriesForRename(superCategory, superCategory, plan);
        }

        return plan;
    }

    private static void walkCategoriesForRename(Category superCategory, Category parent, RenamePlan plan)
    {
        Category[] children = parent.getCategories();
        if (children == null) return;
        Set<String> takenSiblingKeys = new HashSet<>();
        for (Category c : children) if (c != null) takenSiblingKeys.add(c.getKey());
        for (Category c : children)
        {
            if (c == null) continue;
            // Skip user-groups subtree entirely.
            if ("user-groups".equals(c.getKey()) && parent == superCategory) continue;

            String key = c.getKey();
            if (!Tools.isSpecCompliant(key))
            {
                String newKey = Tools.toSpecKey(key, takenSiblingKeys);
                plan.categoryRenames.put(c.getId(), newKey);
                takenSiblingKeys.add(newKey);
            }
            walkCategoriesForRename(superCategory, c, plan);
        }
    }

    // ---------- apply ----------

    private static Collection<Entity> applyPlan(AbstractCachableOperator op, RenamePlan plan) throws RaplaException
    {
        IdentityHashMap<Entity, Entity> editCopies = new IdentityHashMap<>();

        // 1. DTs: edit + setKey on type and/or attributes. Rapla's existing
        // rename mechanism handles the rest:
        // - AttributeImpl.setKey → parent.keyChanged → updateFormatString
        //   on every annotation, which re-emits attribute tokens via
        //   AttributeFunction (holds attribute by ID, returns current key).
        // - The subsequent storeAndRemove dispatch runs
        //   addChangedDynamicTypeDependant which calls commitChange on every
        //   DynamicTypeDependant — Reservations, Allocatables, and any
        //   ClassificationFilter rule in user preferences gets its stored
        //   attributeKey/attributeId updated by id-resolution.
        for (DynamicType original : op.getDynamicTypes())
        {
            if (original == null) continue;
            if (isRaplaInternal(original)) continue;
            String oldDtKey = original.getKey();
            String newDtKey = plan.typeRenames.getOrDefault(oldDtKey, oldDtKey);
            Map<String, String> attrMap = plan.attributeRenames.get(newDtKey);

            boolean needsEdit = !oldDtKey.equals(newDtKey)
                    || (attrMap != null && !attrMap.isEmpty());
            if (!needsEdit) continue;

            DynamicTypeImpl editable = (DynamicTypeImpl) op.editObject((Entity) original, null);
            if (!oldDtKey.equals(newDtKey)) editable.setKey(newDtKey);
            if (attrMap != null)
            {
                for (Attribute a : editable.getAttributes())
                {
                    String newKey = attrMap.get(a.getKey());
                    if (newKey != null) a.setKey(newKey);
                }
            }
            editCopies.put((Entity) original, editable);
        }

        // 2. Categories: rename via id lookup. Category keys aren't embedded
        // in ParsedText annotations (category refs use Category objects /
        // IDs, not string keys), so no propagation needed.
        for (Map.Entry<String, String> e : plan.categoryRenames.entrySet())
        {
            String categoryId = e.getKey();
            String newKey = e.getValue();
            Entity original = (Entity) op.resolve(categoryId, Category.class);
            if (original == null) continue;
            CategoryImpl editable = (CategoryImpl) op.editObject(original, null);
            editable.setKey(newKey);
            editCopies.put(original, editable);
        }

        return new ArrayList<>(editCopies.values());
    }

    // ---------- category assert helper ----------

    private static void walkCategoriesForAssert(Category superCategory, Category parent, List<String> offenders)
    {
        Category[] children = parent.getCategories();
        if (children == null) return;
        for (Category c : children)
        {
            if (c == null) continue;
            if ("user-groups".equals(c.getKey()) && parent == superCategory) continue;
            if (!Tools.isSpecCompliant(c.getKey()))
            {
                offenders.add("CAT:" + c.getId() + ":" + c.getKey());
            }
            walkCategoriesForAssert(superCategory, c, offenders);
        }
    }
}

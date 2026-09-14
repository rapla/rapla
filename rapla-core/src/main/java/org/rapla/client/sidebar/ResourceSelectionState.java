package org.rapla.client.sidebar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical selection model for the calendar-place resource sidebar
 * (PRD 023 Phase 8). Pure-Java, no Swing, no facade — so the click
 * semantics, sticky-during-search mode, and listener fan-out can be
 * pinned by tier-1 tests instead of hand-clicking the JTree.
 *
 * <h2>Why this exists</h2>
 *
 * Before Phase 8, sidebar selection lived in {@code JTree.TreeSelectionModel}
 * (paths), and the click handler read from there to write back into
 * {@code CalendarSelectionModel.selectedObjects}. That made the JTree's
 * path set the de-facto source of truth — fragile every time a search
 * filter swapped the tree model. This class becomes the canonical store;
 * the JTree is reduced to a renderer + click event source.
 *
 * <h2>Entity-agnostic</h2>
 *
 * Selection is stored as opaque {@code Object} keys (typically
 * {@code Allocatable} and {@code User} entities, matching
 * {@code CalendarSelectionModel.setSelectedObjects(Collection<?>)}). The
 * state never inspects the entity layer — equality + hash is enough.
 * Tests use {@code String} labels.
 *
 * <h2>Click semantics</h2>
 *
 * <ul>
 *   <li><b>PLAIN</b> — replace selection (default JTree behaviour). When
 *       sticky mode is on AND search is active, becomes additive (Option b
 *       from the design discussion — protects hidden selection).</li>
 *   <li><b>CTRL</b> — toggle each clicked item.</li>
 *   <li><b>SHIFT</b> — additive (add all without removing existing).
 *       Range-select is the JTree's concern; the state only sees the
 *       resolved row set.</li>
 * </ul>
 */
public final class ResourceSelectionState
{
    public enum ClickIntent { PLAIN, CTRL, SHIFT }

    @FunctionalInterface
    public interface Listener
    {
        void onChange(ResourceSelectionState state);
    }

    private final LinkedHashSet<Object> selected = new LinkedHashSet<>();
    private String searchTerm = "";
    private boolean stickyDuringSearch = false;
    private final List<Listener> listeners = new ArrayList<>();

    // ---------- selection ----------

    public Set<Object> selected()
    {
        return Collections.unmodifiableSet(selected);
    }

    public void setSelected(Collection<?> items)
    {
        LinkedHashSet<Object> incoming = new LinkedHashSet<>();
        if (items != null) incoming.addAll(items);
        if (incoming.equals(selected)) return;
        selected.clear();
        selected.addAll(incoming);
        fire();
    }

    // ---------- search term ----------

    public String searchTerm()
    {
        return searchTerm;
    }

    public void setSearchTerm(String term)
    {
        String normalised = term == null ? "" : term;
        if (normalised.equals(searchTerm)) return;
        this.searchTerm = normalised;
        fire();
    }

    // ---------- sticky mode (Option b) ----------

    public boolean stickyDuringSearch()
    {
        return stickyDuringSearch;
    }

    public void setStickyDuringSearch(boolean sticky)
    {
        if (this.stickyDuringSearch == sticky) return;
        this.stickyDuringSearch = sticky;
        fire();
    }

    // ---------- click handling ----------

    public void handleClick(Collection<?> clicked, ClickIntent intent)
    {
        LinkedHashSet<Object> incoming = new LinkedHashSet<>();
        if (clicked != null) incoming.addAll(clicked);

        LinkedHashSet<Object> next = new LinkedHashSet<>(selected);

        switch (intent)
        {
            case PLAIN:
                if (stickyDuringSearch && !searchTerm.isEmpty())
                {
                    next.addAll(incoming);
                }
                else
                {
                    next = incoming;
                }
                break;
            case CTRL:
                for (Object o : incoming)
                {
                    if (!next.remove(o)) next.add(o);
                }
                break;
            case SHIFT:
                next.addAll(incoming);
                break;
        }

        if (next.equals(selected)) return;
        selected.clear();
        selected.addAll(next);
        fire();
    }

    // ---------- listeners ----------

    public void addListener(Listener l)
    {
        if (l != null) listeners.add(l);
    }

    public void removeListener(Listener l)
    {
        listeners.remove(l);
    }

    private void fire()
    {
        for (Listener l : new ArrayList<>(listeners)) l.onChange(this);
    }
}

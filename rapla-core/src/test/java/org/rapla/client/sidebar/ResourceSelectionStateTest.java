package org.rapla.client.sidebar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.client.sidebar.ResourceSelectionState.ClickIntent;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 contract pin for {@link ResourceSelectionState} — the canonical
 * selection model the sidebar Swing view delegates to (PRD 023 Phase 8).
 *
 * <p>State is entity-agnostic: it stores arbitrary {@code Object} keys
 * (Allocatable, User, …) and lets the view layer translate to/from the
 * domain types. That keeps the click semantics, sticky-during-search
 * mode, and listener fan-out testable without facade / Swing harness.
 */
class ResourceSelectionStateTest
{
    private ResourceSelectionState state;

    @BeforeEach
    void setUp()
    {
        state = new ResourceSelectionState();
    }

    // ---- defaults ----

    @Test
    void defaultsAreEmptyAndStickyOff()
    {
        assertEquals(Set.of(), state.selected());
        assertEquals("", state.searchTerm());
        assertFalse(state.stickyDuringSearch());
    }

    // ---- setSelected ----

    @Test
    void setSelectedStoresTheSet()
    {
        state.setSelected(Set.of("A", "B"));
        assertEquals(Set.of("A", "B"), state.selected());
    }

    @Test
    void setSelectedNullClearsSelection()
    {
        state.setSelected(Set.of("A"));
        state.setSelected(null);
        assertEquals(Set.of(), state.selected());
    }

    @Test
    void setSelectedNoChangeDoesNotFire()
    {
        state.setSelected(Set.of("A", "B"));
        AtomicInteger count = new AtomicInteger();
        state.addListener(s -> count.incrementAndGet());
        state.setSelected(Set.of("A", "B"));
        assertEquals(0, count.get());
    }

    @Test
    void setSelectedChangeFiresOnce()
    {
        AtomicInteger count = new AtomicInteger();
        state.addListener(s -> count.incrementAndGet());
        state.setSelected(Set.of("A"));
        assertEquals(1, count.get());
    }

    // ---- setSearchTerm ----

    @Test
    void setSearchTermNormalizesNullToEmpty()
    {
        state.setSearchTerm(null);
        assertEquals("", state.searchTerm());
    }

    @Test
    void setSearchTermFiresWhenChanged()
    {
        AtomicInteger count = new AtomicInteger();
        state.addListener(s -> count.incrementAndGet());
        state.setSearchTerm("math");
        assertEquals(1, count.get());
    }

    @Test
    void setSearchTermDoesNotFireWhenUnchanged()
    {
        state.setSearchTerm("math");
        AtomicInteger count = new AtomicInteger();
        state.addListener(s -> count.incrementAndGet());
        state.setSearchTerm("math");
        assertEquals(0, count.get());
    }

    // ---- click: PLAIN, no search active ----

    @Test
    void plainClickNoSearchReplacesSelection()
    {
        state.setSelected(Set.of("A", "B"));
        state.handleClick(Set.of("C"), ClickIntent.PLAIN);
        assertEquals(Set.of("C"), state.selected());
    }

    @Test
    void plainClickEmptySetClearsSelection()
    {
        state.setSelected(Set.of("A", "B"));
        state.handleClick(Set.of(), ClickIntent.PLAIN);
        assertEquals(Set.of(), state.selected());
    }

    // ---- click: PLAIN during search, sticky off (default) ----

    @Test
    void plainClickDuringSearchStickyOffReplacesSelection()
    {
        state.setSelected(Set.of("A", "B"));
        state.setSearchTerm("math");
        state.handleClick(Set.of("C"), ClickIntent.PLAIN);
        assertEquals(Set.of("C"), state.selected(),
                "Sticky off: search-mode plain click still replaces (legacy / footgun)");
    }

    // ---- click: PLAIN during search, sticky on (Option b) ----

    @Test
    void plainClickDuringSearchStickyOnAddsToSelection()
    {
        state.setSelected(Set.of("A", "B"));
        state.setSearchTerm("math");
        state.setStickyDuringSearch(true);
        state.handleClick(Set.of("C"), ClickIntent.PLAIN);
        assertEquals(Set.of("A", "B", "C"), state.selected(),
                "Sticky on: search-mode plain click adds (Option b — protects hidden selection)");
    }

    @Test
    void stickyOnButSearchEmptyStillReplacesOnPlainClick()
    {
        state.setSelected(Set.of("A", "B"));
        state.setStickyDuringSearch(true);
        // searchTerm is "" — sticky mode is gated on active search
        state.handleClick(Set.of("C"), ClickIntent.PLAIN);
        assertEquals(Set.of("C"), state.selected(),
                "Sticky only activates when search is non-empty");
    }

    // ---- click: CTRL ----

    @Test
    void ctrlClickAddsNewItems()
    {
        state.setSelected(Set.of("A", "B"));
        state.handleClick(Set.of("C"), ClickIntent.CTRL);
        assertEquals(Set.of("A", "B", "C"), state.selected());
    }

    @Test
    void ctrlClickTogglesExistingItem()
    {
        state.setSelected(Set.of("A", "B"));
        state.handleClick(Set.of("B"), ClickIntent.CTRL);
        assertEquals(Set.of("A"), state.selected());
    }

    @Test
    void ctrlClickMixOfNewAndExistingToggles()
    {
        state.setSelected(Set.of("A", "B"));
        state.handleClick(Set.of("B", "C"), ClickIntent.CTRL);
        assertEquals(Set.of("A", "C"), state.selected());
    }

    // ---- click: SHIFT ----

    @Test
    void shiftClickAddsAll()
    {
        state.setSelected(Set.of("A"));
        state.handleClick(Set.of("B", "C"), ClickIntent.SHIFT);
        assertEquals(Set.of("A", "B", "C"), state.selected());
    }

    @Test
    void shiftClickPreservesExistingEvenIfRedundant()
    {
        state.setSelected(Set.of("A", "B"));
        state.handleClick(Set.of("A", "C"), ClickIntent.SHIFT);
        assertEquals(Set.of("A", "B", "C"), state.selected());
    }

    // ---- listener fan-out ----

    @Test
    void clickFiresExactlyOnce()
    {
        state.setSelected(Set.of("A"));
        AtomicInteger count = new AtomicInteger();
        state.addListener(s -> count.incrementAndGet());
        state.handleClick(Set.of("B"), ClickIntent.PLAIN);
        assertEquals(1, count.get());
    }

    @Test
    void clickWithNoChangeDoesNotFire()
    {
        state.setSelected(Set.of("A"));
        AtomicInteger count = new AtomicInteger();
        state.addListener(s -> count.incrementAndGet());
        // CTRL toggle of nothing — same selection
        state.handleClick(Set.of(), ClickIntent.CTRL);
        assertEquals(0, count.get());
    }

    @Test
    void removedListenerDoesNotFire()
    {
        AtomicInteger count = new AtomicInteger();
        ResourceSelectionState.Listener l = s -> count.incrementAndGet();
        state.addListener(l);
        state.removeListener(l);
        state.setSelected(Set.of("A"));
        assertEquals(0, count.get());
    }

    // ---- iteration order is preserved (LinkedHashSet semantics) ----

    @Test
    void selectionPreservesInsertionOrder()
    {
        Set<Object> in = new LinkedHashSet<>();
        in.add("C"); in.add("A"); in.add("B");
        state.setSelected(in);
        assertEquals("[C, A, B]", state.selected().toString());
    }

    @Test
    void ctrlAddPreservesInsertionOrder()
    {
        state.setSelected(Set.of("A"));
        state.handleClick(Set.of("C"), ClickIntent.CTRL);
        state.handleClick(Set.of("B"), ClickIntent.CTRL);
        assertEquals("[A, C, B]", state.selected().toString());
    }

    // ---- defensive: returned set is unmodifiable ----

    @Test
    void selectedReturnsUnmodifiableView()
    {
        state.setSelected(Set.of("A"));
        Set<Object> view = state.selected();
        try
        {
            view.add("X");
            // If we get here, the view was modifiable — that's a contract violation.
            throw new AssertionError("selected() must return an unmodifiable view");
        }
        catch (UnsupportedOperationException expected)
        {
            // ok
        }
    }

    // ---- edge cases ----

    @Test
    void searchTermStripsThenStoresVerbatim()
    {
        state.setSearchTerm("  math  ");
        assertEquals("  math  ", state.searchTerm(),
                "State stores the term verbatim — trimming/normalisation is the matcher's job");
    }

    @Test
    void handleClickHandlesNullCollectionAsEmpty()
    {
        state.setSelected(Set.of("A", "B"));
        state.handleClick(null, ClickIntent.PLAIN);
        assertEquals(Set.of(), state.selected());
    }

    @Test
    void setSelectedDefensivelyCopies()
    {
        Set<Object> mutable = new HashSet<>(Set.of("A"));
        state.setSelected(mutable);
        mutable.add("X");
        assertEquals(Set.of("A"), state.selected(),
                "State must not be mutated through the caller's collection reference");
    }
}

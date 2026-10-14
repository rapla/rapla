package org.rapla.client.swing.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.rapla.storage.dbrm.UserSummary;

/** PRD 131 R17: the switch-user dialog filters the REST list by username or display name and switches by username. */
class SwitchUserPanelFilterTest
{
    private final List<UserSummary> users = List.of(new UserSummary("export", "Shared Export Account"), new UserSummary("homer", "Simpson Homer"),
            new UserSummary("lonely", ""));

    @Test
    void emptyNeedleKeepsEveryone()
    {
        assertEquals(users, SwitchUserPanel.filter(users, "  "));
        assertEquals(users, SwitchUserPanel.filter(users, null));
    }

    @Test
    void matchesUsernameOrDisplayNameIgnoringCase()
    {
        assertEquals(List.of(users.get(1)), SwitchUserPanel.filter(users, "HOM"));
        assertEquals(List.of(users.get(0)), SwitchUserPanel.filter(users, "shared"));
        assertEquals(List.of(), SwitchUserPanel.filter(users, "nobody"));
    }

    @Test
    void labelShowsTheUsernameNextToTheDisplayName()
    {
        assertEquals("Simpson Homer (homer)", SwitchUserPanel.label(users.get(1)));
        assertEquals("lonely", SwitchUserPanel.label(users.get(2)));
    }
}

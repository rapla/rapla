package org.rapla.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.rapla.client.menu.SessionMenuModel.Entry.LOGOUT;
import static org.rapla.client.menu.SessionMenuModel.Entry.QUIT;
import static org.rapla.client.menu.SessionMenuModel.Entry.QUIT_STAY;
import static org.rapla.client.menu.SessionMenuModel.Entry.RELOGIN;
import static org.rapla.client.menu.SessionMenuModel.Entry.SEPARATOR;
import static org.rapla.client.menu.SessionMenuModel.Entry.SWITCH_BACK;
import static org.rapla.client.menu.SessionMenuModel.Entry.SWITCH_TO;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.rapla.storage.dbrm.TokenStore.Failure;

/** PRD 131 R1, R5-R10, R13, R15, D6: which entries the user menu and the File menu offer in which state. */
class SessionMenuModelTest
{
    @Test
    void storedSessionOffersQuitStayingLoggedInDirectlyAboveLogout()
    {
        assertEquals(List.of(RELOGIN, QUIT_STAY, LOGOUT), SessionMenuModel.fileMenu(false, true));
        assertEquals(List.of(RELOGIN, QUIT_STAY, LOGOUT), SessionMenuModel.userMenu(false, false, true));
    }

    @Test
    void withoutStoredSessionQuitStayIsPresentButDisabled()
    {
        assertEquals(List.of(RELOGIN, QUIT_STAY, LOGOUT), SessionMenuModel.fileMenu(false, false));
        assertEquals(List.of(RELOGIN, QUIT_STAY, LOGOUT), SessionMenuModel.userMenu(false, false, false));
        assertFalse(SessionMenuModel.enabled(QUIT_STAY, false));
        assertTrue(SessionMenuModel.enabled(QUIT_STAY, true));
        assertTrue(SessionMenuModel.enabled(LOGOUT, false));
        assertTrue(SessionMenuModel.enabled(QUIT, false));
    }

    @Test
    void userMenuOffersSwitchUserOnlyWhenTheListIsNotEmpty()
    {
        assertEquals(List.of(SWITCH_TO, SEPARATOR, RELOGIN, QUIT_STAY, LOGOUT), SessionMenuModel.userMenu(false, true, true));
        assertFalse(SessionMenuModel.userMenu(false, false, true).contains(SWITCH_TO));
    }

    @Test
    void fileMenuNeverCarriesTheSwitchEntries()
    {
        assertFalse(SessionMenuModel.fileMenu(false, true).contains(SWITCH_TO));
        assertFalse(SessionMenuModel.fileMenu(true, true).contains(SWITCH_BACK));
    }

    @Test
    void impersonatingHasNoLogoutAndNoReloginButSwitchBackAndSwitchToAndQuit()
    {
        assertEquals(List.of(SWITCH_BACK, SWITCH_TO, SEPARATOR, QUIT), SessionMenuModel.userMenu(true, true, true));
        assertEquals(List.of(SWITCH_BACK, SEPARATOR, QUIT), SessionMenuModel.userMenu(true, false, false));
        assertEquals(List.of(QUIT), SessionMenuModel.fileMenu(true, true));
        assertFalse(SessionMenuModel.logoutIconVisible(true));
        assertTrue(SessionMenuModel.logoutIconVisible(false));
    }

    @Test
    void labelKeysAreTheSessionKeys()
    {
        assertEquals("session.relogin", RELOGIN.labelKey());
        assertEquals("session.quit_stay", QUIT_STAY.labelKey());
        assertEquals("session.quit", QUIT.labelKey());
        assertEquals("session.logout", LOGOUT.labelKey());
        assertEquals("session.switch_back", SWITCH_BACK.labelKey());
        assertEquals("session.switch_to", SWITCH_TO.labelKey());
    }

    @Test
    void storageHintNeedsStayChosenAndAFailure()
    {
        Optional<Failure> failure = Optional.of(new Failure(Path.of("/home/u/.rapla/tokens.json"), "not writable"));
        assertEquals(failure, SessionMenuModel.storageHint(true, failure));
        assertEquals(Optional.empty(), SessionMenuModel.storageHint(false, failure));
        assertEquals(Optional.empty(), SessionMenuModel.storageHint(true, Optional.empty()));
    }
}

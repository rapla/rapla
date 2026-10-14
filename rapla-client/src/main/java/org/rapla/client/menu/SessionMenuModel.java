package org.rapla.client.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.rapla.storage.dbrm.TokenStore.Failure;

/** PRD 131: which session entries the Swing user menu and File menu offer in which state; no UI, no I/O. */
public final class SessionMenuModel
{
    public enum Entry
    {
        SWITCH_BACK("session.switch_back"), SWITCH_TO("session.switch_to"), SEPARATOR(null),
        RELOGIN("session.relogin"), QUIT_STAY("session.quit_stay"), QUIT("session.quit"), LOGOUT("session.logout");

        private final String labelKey;

        Entry(String labelKey) { this.labelKey = labelKey; }

        public String labelKey() { return labelKey; }
    }

    private SessionMenuModel() {}

    /** R15: while impersonating no logout and no "Neu anmelden", but switch back, switch user and plain quit. */
    public static List<Entry> userMenu(boolean impersonating, boolean canSwitchUser, boolean storedSession)
    {
        List<Entry> entries = new ArrayList<>();
        if (impersonating) entries.add(Entry.SWITCH_BACK);
        if (canSwitchUser) entries.add(Entry.SWITCH_TO);
        if (!entries.isEmpty()) entries.add(Entry.SEPARATOR);
        entries.addAll(fileMenu(impersonating, storedSession));
        return entries;
    }

    /** R5-R8: "Beenden (ohne Abmelden)" directly above "Abmelden", disabled without a stored session ({@link #enabled}). */
    public static List<Entry> fileMenu(boolean impersonating, boolean storedSession)
    {
        if (impersonating) return List.of(Entry.QUIT);
        return List.of(Entry.RELOGIN, Entry.QUIT_STAY, Entry.LOGOUT);
    }

    public static boolean enabled(Entry entry, boolean storedSession)
    {
        return entry != Entry.QUIT_STAY || storedSession;
    }

    public static boolean logoutIconVisible(boolean impersonating)
    {
        return !impersonating;
    }

    /** R13: the hint shows only if the user chose stay-signed-in and storing failed. */
    public static Optional<Failure> storageHint(boolean rememberChosen, Optional<Failure> failure)
    {
        return rememberChosen ? failure : Optional.empty();
    }
}

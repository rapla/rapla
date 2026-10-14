package org.rapla.client.swing.internal;

import java.util.List;
import java.util.Optional;

import javax.swing.JMenuItem;

import org.rapla.RaplaResources;
import org.rapla.client.Application;
import org.rapla.client.PopupContext;
import org.rapla.client.UserClientService;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.menu.SessionMenuModel;
import org.rapla.client.menu.SessionMenuModel.Entry;
import org.rapla.client.swing.SwingSafe;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.dbrm.ConsentingTokenStore;
import org.rapla.storage.dbrm.TokenStore;
import org.rapla.storage.dbrm.UserSummary;
import org.rapla.storage.dbrm.UsersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** PRD 131: the session entries shared by the user menu (top right) and the File menu; the decisions live in {@link SessionMenuModel}. */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
public class SessionMenu
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionMenu.class);

    private final UserClientService service;
    private final ApplicationEventBus appEventBus;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final TokenStore tokenStore;
    private final UsersService usersService;
    private final CommandScheduler scheduler;
    private final RaplaResources i18n;
    private volatile boolean canSwitchUser;

    @Autowired
    public SessionMenu(UserClientService service, ApplicationEventBus appEventBus, DialogUiFactoryInterface dialogUiFactory, TokenStore tokenStore,
            UsersService usersService, CommandScheduler scheduler, RaplaResources i18n)
    {
        this.service = service;
        this.appEventBus = appEventBus;
        this.dialogUiFactory = dialogUiFactory;
        this.tokenStore = tokenStore;
        this.usersService = usersService;
        this.scheduler = scheduler;
        this.i18n = i18n;
        scheduler.supply(usersService::list).thenAccept(users -> canSwitchUser = !users.isEmpty())
                .exceptionally(ex -> LOGGER.debug("switch-user list not available: {}", ex.toString()));
    }

    public boolean impersonating()
    {
        return service.canSwitchBack();
    }

    private boolean storedSession()
    {
        try { return tokenStore.read().isPresent(); }
        catch (Throwable t) { return false; }
    }

    public boolean enabled(Entry entry)
    {
        return SessionMenuModel.enabled(entry, storedSession());
    }

    public List<Entry> userEntries()
    {
        return SessionMenuModel.userMenu(impersonating(), canSwitchUser, storedSession());
    }

    public List<Entry> fileEntries()
    {
        return SessionMenuModel.fileMenu(impersonating(), storedSession());
    }

    /** R13: set when the user chose stay-signed-in and the token could not be stored; names the attempted path. */
    public Optional<TokenStore.Failure> storageHint()
    {
        boolean remembers = tokenStore instanceof ConsentingTokenStore consenting && consenting.remembers();
        return SessionMenuModel.storageHint(remembers, tokenStore.failure());
    }

    public String label(Entry entry)
    {
        return i18n.getString(entry.labelKey());
    }

    public JMenuItem item(Entry entry)
    {
        JMenuItem item = new JMenuItem(label(entry));
        item.addActionListener(e -> run(entry));
        item.setEnabled(enabled(entry));
        if (entry == Entry.LOGOUT) item.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.logout")));
        if (entry == Entry.RELOGIN) item.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.restart")));
        return item;
    }

    /** R5-R8, R15, OQ5: no confirmation. */
    public void run(Entry entry)
    {
        PopupContext popupContext = dialogUiFactory.createPopupContext(null);
        switch (entry)
        {
            case RELOGIN -> service.restart();
            case QUIT_STAY, QUIT -> appEventBus.publish(new ApplicationEvent(Application.CLOSE_ACTIVITY_ID, "", popupContext, null));
            case LOGOUT -> service.logoutAndExit();
            case SWITCH_BACK -> {
                try { service.switchTo(null); }
                catch (RaplaException ex) { dialogUiFactory.showException(ex, popupContext); }
            }
            case SWITCH_TO -> openSwitchDialog(popupContext);
            case SEPARATOR -> { }
        }
    }

    private void openSwitchDialog(PopupContext popupContext)
    {
        scheduler.supply(usersService::list)
                .thenAccept(users -> SwingSafe.invokeLater(() -> showSwitchDialog(users, popupContext)))
                .exceptionally(ex -> SwingSafe.invokeLater(() -> dialogUiFactory.showException(ex, popupContext)));
    }

    private void showSwitchDialog(List<UserSummary> users, PopupContext popupContext)
    {
        DialogInterface[] dialog = new DialogInterface[1];
        SwitchUserPanel panel = new SwitchUserPanel(users, i18n.getString("username"),
                selected -> { if (dialog[0] != null) dialog[0].getAction(0).setEnabled(selected != null); },
                () -> dialog[0].getAction(0).execute());
        dialog[0] = dialogUiFactory.createContentDialog(popupContext, panel, new String[] { i18n.getString("session.switch_button"), i18n.getString("cancel") });
        dialog[0].setTitle(i18n.getString("session.switch_title"));
        dialog[0].getAction(0).setEnabled(panel.selectedUsername() != null);
        dialog[0].start(true).thenAccept(choice -> {
            String username = panel.selectedUsername();
            if (choice == 0 && username != null) switchTo(username, popupContext);
        });
        panel.focusFilter();
    }

    private void switchTo(String username, PopupContext popupContext)
    {
        scheduler.supply(() -> { service.switchToUser(username); return null; })
                .exceptionally(ex -> {
                    LOGGER.warn("switch to '{}' failed: {}", username, ex.toString());
                    SwingSafe.invokeLater(() -> dialogUiFactory.showWarning(i18n.format("session.switch_failed", username), popupContext));
                });
    }
}

/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.swing.internal;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.client.Application;
import org.rapla.client.CalendarPlacePresenter;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.UserClientService;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.event.CalendarEventBus;
import org.rapla.client.event.OwnReservationsEvent;
import org.rapla.client.extensionpoints.AdminMenuExtension;
import org.rapla.client.extensionpoints.EditMenuExtension;
import org.rapla.client.extensionpoints.ExportMenuExtension;
import org.rapla.client.extensionpoints.HelpMenuExtension;
import org.rapla.client.extensionpoints.ImportMenuExtension;
import org.rapla.client.extensionpoints.RaplaMenuExtension;
import org.rapla.client.extensionpoints.ViewMenuExtension;
import org.rapla.client.internal.admin.client.TypeCategoryTask;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.menu.UserAction;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.action.RestartRaplaAction;
import org.rapla.client.swing.internal.action.RestartServerAction;
import org.rapla.client.swing.internal.action.SaveableToggleAction;
import org.rapla.client.swing.internal.edit.TemplateEdit;
import org.rapla.client.swing.internal.print.PrintAction;
import org.rapla.client.swing.internal.view.LicenseInfoUI;
import org.rapla.client.swing.toolkit.ActionWrapper;
import org.rapla.client.swing.toolkit.HTMLView;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandHistoryChangedListener;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.ConfigTools;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.client.internal.admin.client.AdminUserTask;
import org.rapla.scheduler.Promise;
import org.rapla.storage.PermissionController;
import org.rapla.storage.dbrm.RestartServer;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Stream;

@Singleton
public class RaplaMenuBar extends RaplaGUIComponent
{
    final JMenuItem exit;
    final JMenuItem redo;
    final JMenuItem undo;
    JMenuItem templateEdit;
    private final EditController editController;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final Provider<TemplateEdit> templateEditFactory;
    private CalendarSelectionModel model;
    Provider<LicenseInfoUI> licenseInfoUIProvider;
    final private ApplicationEventBus appEventBus;
    RaplaMenuItem ownReservationsMenu;
    private final RaplaSystemInfo systemInfo;
    private final MenuItemFactory menuItemFactory;
    private final Provider<UserAction> userActionProvider;


    @Inject public RaplaMenuBar(RaplaMenuBarContainer menuBarContainer, ClientFacade clientFacade, RaplaSystemInfo systemInfo, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            PrintAction printAction, Set<AdminMenuExtension> adminMenuExt, Set<EditMenuExtension> editMenuExt, Set<ViewMenuExtension> viewMenuExt, Set<HelpMenuExtension> helpMenuExt, Set<ImportMenuExtension> importMenuExt,
            Set<ExportMenuExtension> exportMenuExt, EditController editController, CalendarSelectionModel model, UserClientService clientService, RestartServer restartServerService,
            DialogUiFactoryInterface dialogUiFactory, Provider<TemplateEdit> templateEditFactory, Provider<LicenseInfoUI> licenseInfoUIProvider, CalendarEventBus eventBus, ApplicationEventBus appEventBus, MenuItemFactory menuItemFactory,
            Provider<UserAction> userActionProvider)            throws RaplaInitializationException
    {
        super(clientFacade, i18n, raplaLocale, logger);
        this.systemInfo = systemInfo;
        this.model = model;
        this.licenseInfoUIProvider = licenseInfoUIProvider;
        this.editController = editController;
        this.dialogUiFactory = dialogUiFactory;
        this.templateEditFactory = templateEditFactory;
        this.appEventBus = appEventBus;
        this.menuItemFactory = menuItemFactory;
        this.userActionProvider = userActionProvider;

        RaplaMenu editMenu = menuBarContainer.getEditMenu();
        RaplaMenu viewMenu = menuBarContainer.getViewMenu();
        RaplaMenu systemMenu = menuBarContainer.getSystemMenu();
        RaplaMenu adminMenu = menuBarContainer.getAdminMenu();
        RaplaMenu extraMenu = menuBarContainer.getExtraMenu();
        RaplaMenu importMenu = menuBarContainer.getImportMenu();
        RaplaMenu exportMenu = menuBarContainer.getExportMenu();

        User user;
        try
        {
            user = getUser();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
        if (user.isAdmin())
        {
            addPluginExtensions(adminMenuExt, adminMenu);
        }
        addPluginExtensions(importMenuExt, importMenu);
        addPluginExtensions(exportMenuExt, exportMenu);
        addPluginExtensions(helpMenuExt, extraMenu);
        addPluginExtensions(viewMenuExt, viewMenu);
        addPluginExtensions(editMenuExt, editMenu);

        systemMenu.add(new JSeparator());

        JMenuItem printMenu = new JMenuItem(getString("print"));
        systemMenu.setMnemonic('F');
        printMenu.setAction(new ActionWrapper(printAction));
        printAction.setEnabled(true);
        printAction.setModel(model);
        systemMenu.add(printMenu);

        systemMenu.add(new JSeparator());

        if (clientService.canSwitchBack())
        {
            final UserAction userAction = userActionProvider.get().setPopupContext(null).setSwitchToUser();
            userAction.setEnabled( true );
            adminMenu.addMenuItem(userAction.createMenuEntry());
        }

        boolean server = restartServerService.isRestartPossible();
        if (server && isAdmin())
        {
            JMenuItem restartServer = new JMenuItem();
            restartServer.setAction(new ActionWrapper(new RestartServerAction(clientFacade, i18n, raplaLocale, logger, restartServerService)));
            adminMenu.add(restartServer);
        }

        Listener listener = new Listener();
        JMenuItem restart = new JMenuItem();
        restart.setAction(new ActionWrapper(new RestartRaplaAction(clientFacade, i18n, raplaLocale, logger, clientService)));
        systemMenu.add(restart);

        systemMenu.setMnemonic('F');
        exit = new JMenuItem(getString("exit"));
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK));
        exit.setMnemonic('x');
        exit.addActionListener(listener);
        systemMenu.add(exit);

        redo = new JMenuItem(getString("redo"));
        undo = new JMenuItem(getString("undo"));
        undo.setToolTipText(getString("undo"));
        setIcon(undo,i18n.getIcon("icon.undo"));
        redo.addActionListener(listener);
        undo.addActionListener(listener);

        redo.setToolTipText(getString("redo"));
        setIcon(redo,i18n.getIcon("icon.redo"));
        getCommandHistory().addCommandHistoryChangedListener(listener);

        undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK));
        redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK));

        undo.setEnabled(false);
        redo.setEnabled(false);
        editMenu.insertBeforeId(()->undo, "EDIT_BEGIN");
        editMenu.insertBeforeId(()->redo, "EDIT_BEGIN");

        RaplaMenuItem userOptions = new RaplaMenuItem("userOptions");
        editMenu.add(userOptions);
        if (isTemplateEditAllowed(user))
        {
            templateEdit = new RaplaMenuItem("template");
            updateTemplateText();
            templateEdit.addActionListener(listener);
            editMenu.add(templateEdit);
        }

        boolean modifyPreferencesAllowed = isModifyPreferencesAllowed();
        if (modifyPreferencesAllowed)
        {
            try
            {
                userOptions.setAction(createOptionAction(getFacade().getPreferences(user)));
            }
            catch (RaplaException e)
            {
                throw new RaplaInitializationException(e);
            }
        }
        else
        {
            userOptions.setVisible(false);
        }

        try
        {
            {
                SaveableToggleAction action = new SaveableToggleAction(clientFacade, i18n, raplaLocale, logger, "show_tips", RaplaBuilder.SHOW_TOOLTIP_CONFIG_ENTRY, dialogUiFactory);
                RaplaMenuItem menu = createMenuItem(action);
                viewMenu.insertBeforeId(menu, "view_save");
                action.setEnabled(modifyPreferencesAllowed);
            }
            {
                SaveableToggleAction action = new SaveableToggleAction(clientFacade, i18n, raplaLocale, logger, CalendarPlacePresenter.SHOW_CONFLICTS_MENU_ENTRY,
                        CalendarPlacePresenter.SHOW_CONFLICTS_CONFIG_ENTRY, dialogUiFactory);
                RaplaMenuItem menu = createMenuItem(action);
                viewMenu.insertBeforeId(menu, "view_save");
                action.setEnabled(modifyPreferencesAllowed);
            }
        }
        catch(RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }

        boolean canAdminUsers = PermissionController.canAdminUsers(user);
        if (canAdminUsers)
        {
            RaplaMenuItem  userEditAction = new RaplaMenuItem("useradmin");
            final String name = getString("user") +"/"+ getString("groups") ;
            userEditAction.setText( name);
            final Icon icon = RaplaImages.getIcon(i18n.getIcon("icon.tree.persons"));
            userEditAction.setIcon( icon);
            userEditAction.addActionListener((evt)->
                    {
                        final PopupContext popupContext = dialogUiFactory.createPopupContext(() -> getMainComponent());
                        ApplicationEvent.ApplicationEventContext context = null;
                        String applicationEventId = AdminUserTask.USER_ADMIN_ID;
                        String info = applicationEventId;
                        final ApplicationEvent event = new ApplicationEvent(applicationEventId, info, popupContext, context);
                        appEventBus.publish(event);
                    });
            adminMenu.add( userEditAction  );
        }
        if (isAdmin())
        {
            RaplaMenuItem  typeAdmin = new RaplaMenuItem("typeadmin");
            final String name = getString("types") + "/" + getString("categories")  + "/" + i18n.getString("periods");
            typeAdmin.setText( name);
            final Icon icon = RaplaImages.getIcon(i18n.getIcon("icon.tree"));
            typeAdmin.setIcon( icon);
            typeAdmin.addActionListener((evt)->
            {
                final PopupContext popupContext = dialogUiFactory.createPopupContext(() -> getMainComponent());
                ApplicationEvent.ApplicationEventContext context = null;
                String applicationEventId = TypeCategoryTask.ID;
                String info = applicationEventId;
                final ApplicationEvent event = new ApplicationEvent(applicationEventId, info, popupContext, context);
                appEventBus.publish(event);
            });
            adminMenu.add( typeAdmin  );

            RaplaMenuItem adminOptions = new RaplaMenuItem("adminOptions");
            try
            {
                adminOptions.setAction(createOptionAction(getQuery().getSystemPreferences()));
            }
            catch (RaplaException e)
            {
                throw new RaplaInitializationException(e);
            }
            adminMenu.add(adminOptions);
        }


        ownReservationsMenu = new RaplaMenuItem("only_own_reservations");
        ownReservationsMenu.setText(i18n.getString("only_own_reservations"));
        ownReservationsMenu = new RaplaMenuItem("only_own_reservations");
        ownReservationsMenu.addActionListener(e -> {
            boolean isSelected = model.isOnlyCurrentUserSelected();
            // switch selection options
            model.setOption(CalendarModel.ONLY_MY_EVENTS, isSelected ? "false" : "true");
            eventBus.publish( new OwnReservationsEvent());
        });

        ownReservationsMenu.setText(i18n.getString("only_own_reservations"));
        setIcon(ownReservationsMenu,i18n.getIcon("icon.unchecked"));

        RaplaMenuItem info = new RaplaMenuItem("info");
        info.setAction(createInfoAction());
        extraMenu.add(info);

        // within the help menu we need another point for the license
        RaplaMenuItem license = new RaplaMenuItem("license");
        // give this menu item an action to perform on click
        license.setAction(createLicenseAction());
        // add the license dialog below the info entry
        extraMenu.add(license);

        adminMenu.setEnabled(adminMenu.getMenuComponentCount() != 0);
        exportMenu.setEnabled(exportMenu.getMenuComponentCount() != 0);
        importMenu.setEnabled(importMenu.getMenuComponentCount() != 0);
    }

    public void setIcon(JMenuItem menuItem, I18nIcon icon)
    {
        menuItem.setIcon(RaplaImages.getIcon( icon));
    }

    public void updateView(ModificationEvent evt)
    {

        boolean isSelected = model.isOnlyCurrentUserSelected();
        setIcon(ownReservationsMenu,isSelected ? i18n.getIcon("icon.checked") : i18n.getIcon("icon.unchecked"));
        ownReservationsMenu.setSelected(isSelected);
        updateTemplateText();
    }

    public CommandHistory getCommandHistory()
    {
        return getClientFacade().getCommandHistory();
    }

    private RaplaMenuItem createMenuItem(SaveableToggleAction action) throws RaplaException
    {
        RaplaMenuItem menu = new RaplaMenuItem(action.getName());
        menu.setAction(new ActionWrapper(action, getI18n()));
        final User user = getUser();
        final Preferences preferences = getQuery().getPreferences(user);
        boolean selected = preferences.getEntryAsBoolean(action.getConfigEntry(), true);
        menu.setSelected(selected);
        setIcon(menu,selected ? i18n.getIcon("icon.checked") : i18n.getIcon("icon.unchecked"));
        return menu;
    }

    protected void updateTemplateText()
    {
        if (templateEdit == null)
        {
            return;
        }
        String editString = getString("edit-templates");
        String exitString = getString("close-template");
        templateEdit.setText(isTemplateEdit() ? exitString : editString);
    }

    protected boolean isTemplateEdit()
    {
        return getClientFacade().getTemplate() != null;
    }


    class Listener implements ActionListener, CommandHistoryChangedListener
    {

        public void historyChanged()
        {
            CommandHistory history = getCommandHistory();
            redo.setEnabled(history.canRedo());
            undo.setEnabled(history.canUndo());
            redo.setText(getString("redo") + ": " + history.getRedoText());
            undo.setText(getString("undo") + ": " + history.getUndoText());
        }

        public void actionPerformed(ActionEvent e)
        {
            Object source = e.getSource();
            if (source == exit)
            {
                appEventBus.publish( new ApplicationEvent(Application.CLOSE_ACTIVITY_ID,"", createPopupContext(),null));
            }
            else if (source == templateEdit)
            {
                if (isTemplateEdit())
                {
                    getClientFacade().setTemplate(null);
                }
                else
                {
                    try
                    {
                        TemplateEdit edit = templateEditFactory.get();
                        edit.startTemplateEdit();
                        updateTemplateText();
                    }
                    catch (Exception ex)
                    {
                        dialogUiFactory.showException(ex, createPopupContext());
                    }
                }
            }
            else
            {
                CommandHistory commandHistory = getCommandHistory();
                final Promise<Void> promise;
                if (source == redo)
                {
                    promise = commandHistory.redo();
                }
                else if (source == undo)
                {
                    promise = commandHistory.undo();
                }
                else {
                    promise = null;
                }
                promise.exceptionally( (ex) ->
                    dialogUiFactory.showException(ex, createPopupContext())
                );
            }
        }
    }

    @NotNull
    private PopupContext createPopupContext()
    {
        return dialogUiFactory.createPopupContext( null);
    }

    private void addPluginExtensions(Set<? extends RaplaMenuExtension> points, RaplaMenu menu)
    {
        for (RaplaMenuExtension menuItem : points)
        {
            menu.add((JComponent)menuItem.getComponent());
        }
    }

    private Action createOptionAction(final Preferences preferences)
    {
        AbstractAction action = new AbstractAction()
        {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent arg0)
            {
                editController.edit(preferences, dialogUiFactory.createPopupContext( null));
            }

        };
        action.putValue(Action.SMALL_ICON, RaplaImages.getIcon(i18n.getIcon("icon.options")));
        action.putValue(Action.NAME, getString("options"));
        return action;
    }

    private Action createInfoAction()
    {
        final String name = getString("info");
        final Icon icon = RaplaImages.getIcon(i18n.getIcon("icon.info_small"));

        AbstractAction action = new AbstractAction()
        {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e)
            {
                    HTMLView infoText = new HTMLView();
                    infoText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                    String javaversion;
                    try
                    {
                        javaversion = System.getProperty("java.version");
                    }
                    catch (SecurityException ex)
                    {
                        javaversion = "-";
                        getLogger().warn("Permission to system properties denied!");
                    }

                    String mainText = systemInfo.infoText(javaversion);
                    StringBuffer completeText = new StringBuffer();
                    completeText.append(mainText);
                    URL librariesURL = null;
                    try
                    {
                        Enumeration<URL> resources = ConfigTools.class.getClassLoader().getResources("META-INF/readme.txt");
                        if (resources.hasMoreElements())
                        {
                            librariesURL = resources.nextElement();
                        }
                    }
                    catch (IOException e1)
                    {
                    }
                    if (librariesURL != null)
                    {
                        completeText.append("<pre>\n\n\n");
                        BufferedReader bufferedReader = null;
                        try
                        {
                            bufferedReader = new BufferedReader(new InputStreamReader(librariesURL.openStream()));
                            while (true)
                            {
                                String line = bufferedReader.readLine();
                                if (line == null)
                                {
                                    break;
                                }
                                completeText.append(line);
                                completeText.append("\n");
                            }
                        }
                        catch (IOException ex)
                        {
                            try
                            {
                                if (bufferedReader != null)
                                {
                                    bufferedReader.close();
                                }
                            }
                            catch (IOException e1)
                            {
                            }
                        }
                        completeText.append("</pre>");
                    }

                    String body = completeText.toString();
                    infoText.setBody(body);
                    final JScrollPane content = new JScrollPane(infoText);
                    content.setSize( 780,580);
                    DialogInterface dialog = dialogUiFactory.createContentDialog(createPopupContext(), content, new String[] { getString("ok") });
                    dialog.setTitle(name);
                    dialog.start(false);

                    SwingUtilities.invokeLater(() -> content.getViewport().setViewPosition(new Point(0, 0)));
            }

        };
        action.putValue(Action.SMALL_ICON, icon);
        action.putValue(Action.NAME, name);
        return action;
    }

    /**
     * the action to perform when someone clicks on the license entry in the
     * help section of the menu menubar
     *
     * this method is a modified version of the existing method createInfoAction()
     */
    private Action createLicenseAction()
    {
        final String name = systemInfo.getString("licensedialog.title");
        final Icon icon = RaplaImages.getIcon(i18n.getIcon("icon.info_small"));

        // overwrite the cass AbstractAction to design our own
        AbstractAction action = new AbstractAction()
        {
            private static final long serialVersionUID = 1L;

            // overwrite the actionPerformed method that is called on click
            public void actionPerformed(ActionEvent e)
            {
                // we need a new instance of HTMLView to visualize the short
                // version of the license text including the two links
                HTMLView licenseText = new HTMLView();
                // giving the gui element some borders
                licenseText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                // we look up the text was originally meant for the welcome field
                // and put it into a new instance of RaplaWidget
                RaplaWidget welcomeField = licenseInfoUIProvider.get();
                // the following creates the dialog that pops up, when we click
                // on the license entry within the help section of the menu menubar
                // we call the createInfoDialog Method of the DialogUI class and give it all necessary things
                final JScrollPane content = new JScrollPane((Component) welcomeField.getComponent());
                content.setSize(550, 250);
                DialogInterface dialog = dialogUiFactory.createContentDialog(createPopupContext(), content,
                        new String[] { getString("ok") });
                // setting the dialog's title
                dialog.setTitle(name);
                // and the size of the popup window

                // but I honestly have no clue what this startNoPack() does
                dialog.start(false);
            }
        };

        action.putValue(Action.SMALL_ICON, icon);
        action.putValue(Action.NAME, name);
        return action;
    }

}




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

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.RaplaResources;
import org.rapla.client.CalendarPlacePresenter;
import org.rapla.client.EditController;
import org.rapla.client.ReservationController;
import org.rapla.client.UserClientService;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.OwnReservationsEvent;
import org.rapla.client.extensionpoints.AdminMenuExtension;
import org.rapla.client.extensionpoints.EditMenuExtension;
import org.rapla.client.extensionpoints.ExportMenuExtension;
import org.rapla.client.extensionpoints.HelpMenuExtension;
import org.rapla.client.extensionpoints.ImportMenuExtension;
import org.rapla.client.extensionpoints.ViewMenuExtension;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.action.RestartRaplaAction;
import org.rapla.client.swing.internal.action.RestartServerAction;
import org.rapla.client.swing.internal.action.SaveableToggleAction;
import org.rapla.client.swing.internal.action.user.UserAction;
import org.rapla.client.swing.internal.edit.TemplateEdit;
import org.rapla.client.swing.internal.edit.TemplateEdit.TemplateEditFactory;
import org.rapla.client.swing.internal.print.PrintAction;
import org.rapla.client.swing.internal.view.LicenseInfoUI;
import org.rapla.client.swing.toolkit.ActionWrapper;
import org.rapla.client.swing.toolkit.HTMLView;
import org.rapla.client.swing.toolkit.IdentifiableMenuEntry;
import org.rapla.client.swing.toolkit.RaplaFrame;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.client.RaplaWidget;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandHistoryChangedListener;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.ConfigTools;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.scheduler.Promise;
import org.rapla.storage.dbrm.RestartServer;

@Singleton
public class RaplaMenuBar extends RaplaGUIComponent
{
    final JMenuItem exit;
    final JMenuItem redo;
    final JMenuItem undo;
    JMenuItem templateEdit;
    private final EditController editController;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final TemplateEditFactory templateEditFactory;
    private CalendarSelectionModel model;
    Provider<LicenseInfoUI> licenseInfoUIProvider;
    final private EventBus eventBus;
    RaplaMenuItem ownReservationsMenu;


    @Inject public RaplaMenuBar(RaplaMenuBarContainer menuBarContainer, ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            PrintAction printAction, Set<AdminMenuExtension> adminMenuExt, Set<EditMenuExtension> editMenuExt, Set<ViewMenuExtension> viewMenuExt,
            Set<HelpMenuExtension> helpMenuExt, Set<ImportMenuExtension> importMenuExt, Set<ExportMenuExtension> exportMenuExt, MenuFactory menuFactory,
            EditController editController, CalendarSelectionModel model, UserClientService clientService, RestartServer restartServerService,
            RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, TemplateEditFactory templateEditFactory,
            Provider<LicenseInfoUI> licenseInfoUIProvider, ReservationController reservationController, EventBus eventBus)            throws RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger);
        this.model = model;
        this.licenseInfoUIProvider = licenseInfoUIProvider;
        this.editController = editController;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.templateEditFactory = templateEditFactory;
        this.eventBus = eventBus;


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
            JMenuItem switchBack = new JMenuItem();
            switchBack.setAction(new ActionWrapper(new UserAction(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), null, clientService, editController, raplaImages, dialogUiFactory).setSwitchToUser()));
            adminMenu.add(switchBack);
        }

        boolean server = restartServerService.isRestartPossible();
        if (server && isAdmin())
        {
            JMenuItem restartServer = new JMenuItem();
            restartServer.setAction(new ActionWrapper(new RestartServerAction(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), restartServerService, raplaImages)));
            adminMenu.add(restartServer);
        }

        Listener listener = new Listener();
        JMenuItem restart = new JMenuItem();
        restart.setAction(new ActionWrapper(new RestartRaplaAction(facade, getI18n(), getRaplaLocale(), getLogger(), clientService, raplaImages)));
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
        undo.setIcon(raplaImages.getIconFromKey("icon.undo"));
        redo.addActionListener(listener);
        undo.addActionListener(listener);

        redo.setToolTipText(getString("redo"));
        redo.setIcon(raplaImages.getIconFromKey("icon.redo"));
        getCommandHistory().addCommandHistoryChangedListener(listener);

        undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK));
        redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK));

        undo.setEnabled(false);
        redo.setEnabled(false);
        editMenu.insertBeforeId(undo, "EDIT_BEGIN");
        editMenu.insertBeforeId(redo, "EDIT_BEGIN");

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
                userOptions.setAction(createOptionAction(getQuery().getPreferences()));
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
                SaveableToggleAction action = new SaveableToggleAction(facade, getI18n(), getRaplaLocale(), getLogger(), "show_tips", RaplaBuilder.SHOW_TOOLTIP_CONFIG_ENTRY, dialogUiFactory);
                RaplaMenuItem menu = createMenuItem(action);
                viewMenu.insertBeforeId(menu, "view_save");
                action.setEnabled(modifyPreferencesAllowed);
            }
            {
                SaveableToggleAction action = new SaveableToggleAction(facade, getI18n(), getRaplaLocale(), getLogger(), CalendarPlacePresenter.SHOW_CONFLICTS_MENU_ENTRY,
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

        if (isAdmin())
        {
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
        ownReservationsMenu.addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                boolean isSelected = model.isOnlyCurrentUserSelected();
                // switch selection options
                model.setOption(CalendarModel.ONLY_MY_EVENTS, isSelected ? "false" : "true");
                eventBus.fireEvent( new OwnReservationsEvent());
            }
        });

        ownReservationsMenu.setText(i18n.getString("only_own_reservations"));
        ownReservationsMenu.setIcon(raplaImages.getIconFromKey("icon.unchecked"));

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
        getUpdateModule().addModificationListener(listener);
    }

    public void updateView(ModificationEvent evt)
    {

        boolean isSelected = model.isOnlyCurrentUserSelected();
        ownReservationsMenu.setIcon(isSelected ? raplaImages.getIconFromKey("icon.checked") : raplaImages.getIconFromKey("icon.unchecked"));
        ownReservationsMenu.setSelected(isSelected);

    }

    public CommandHistory getCommandHistory()
    {
        return getUpdateModule().getCommandHistory();
    }

    private RaplaMenuItem createMenuItem(SaveableToggleAction action) throws RaplaException
    {
        RaplaMenuItem menu = new RaplaMenuItem(action.getName());
        menu.setAction(new ActionWrapper(action, getI18n(), raplaImages));
        final User user = getUser();
        final Preferences preferences = getQuery().getPreferences(user);
        boolean selected = preferences.getEntryAsBoolean(action.getConfigEntry(), true);
        if (selected)
        {
            menu.setSelected(true);
            menu.setIcon(raplaImages.getIconFromKey("icon.checked"));
        }
        else
        {
            menu.setSelected(false);
            menu.setIcon(raplaImages.getIconFromKey("icon.unchecked"));
        }
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
        return getUpdateModule().getTemplate() != null;
    }


    class Listener implements ActionListener, CommandHistoryChangedListener, ModificationListener
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
                RaplaFrame mainComponent = (RaplaFrame) getMainComponent();
                mainComponent.close();
            }
            else if (source == templateEdit)
            {
                if (isTemplateEdit())
                {
                    getUpdateModule().setTemplate(null);
                }
                else
                {
                    try
                    {
                        TemplateEdit edit = templateEditFactory.create();
                        edit.startTemplateEdit();
                        updateTemplateText();
                    }
                    catch (Exception ex)
                    {
                        dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
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
                {
                    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
                    return null;
                }
                );
            }
        }

        public void dataChanged(ModificationEvent evt) throws RaplaException
        {
            updateTemplateText();
        }

    }

    private void addPluginExtensions(Set<? extends IdentifiableMenuEntry> points, RaplaMenu menu)
    {
        for (IdentifiableMenuEntry menuItem : points)
        {
            MenuElement menuElement = menuItem.getMenuElement();
            menu.add(menuElement.getComponent());
        }
    }

    private Action createOptionAction(final Preferences preferences)
    {
        AbstractAction action = new AbstractAction()
        {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent arg0)
            {
                editController.edit(preferences, createPopupContext(getMainComponent(), null));
            }

        };
        action.putValue(Action.SMALL_ICON, raplaImages.getIconFromKey("icon.options"));
        action.putValue(Action.NAME, getString("options"));
        return action;
    }

    private Action createInfoAction()
    {
        final String name = getString("info");
        final Icon icon = raplaImages.getIconFromKey("icon.info_small");

        AbstractAction action = new AbstractAction()
        {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e)
            {
                try
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

                    String mainText = getI18n().infoText(javaversion);
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
                    DialogInterface dialog = dialogUiFactory.create(new SwingPopupContext(getMainComponent(), null), false, content, new String[] { getString("ok") });
                    dialog.setTitle(name);
                    dialog.setSize(780, 580);
                    dialog.start(false);

                    SwingUtilities.invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            content.getViewport().setViewPosition(new Point(0, 0));
                        }

                    });
                }
                catch (RaplaException ex)
                {
                    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
                }
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
        final String name = getString("licensedialog.title");
        final Icon icon = raplaImages.getIconFromKey("icon.info_small");

        // overwrite the cass AbstractAction to design our own
        AbstractAction action = new AbstractAction()
        {
            private static final long serialVersionUID = 1L;

            // overwrite the actionPerformed method that is called on click
            public void actionPerformed(ActionEvent e)
            {
                try
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
                    // we call the create Method of the DialogUI class and give it all necessary things
                    DialogInterface dialog = dialogUiFactory.create(new SwingPopupContext(getMainComponent(), null), true, new JScrollPane((Component) welcomeField.getComponent()),
                            new String[] { getString("ok") });
                    // setting the dialog's title
                    dialog.setTitle(name);
                    // and the size of the popup window
                    dialog.setSize(550, 250);
                    // but I honestly have no clue what this startNoPack() does
                    dialog.start(false);
                }
                catch (RaplaException ex)
                {
                    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
                }
            }
        };

        action.putValue(Action.SMALL_ICON, icon);
        action.putValue(Action.NAME, name);
        return action;
    }

}




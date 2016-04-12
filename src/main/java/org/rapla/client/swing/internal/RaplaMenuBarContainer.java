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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

import org.rapla.RaplaResources;
import org.rapla.client.swing.internal.common.InternMenus;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenubar;
import org.rapla.client.swing.toolkit.RaplaSeparator;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;

@Singleton
public class RaplaMenuBarContainer
{
    JMenuItem templateEdit;
    final private RaplaMenubar menubar;

    private final RaplaMenu calendarSettings;
    private final RaplaMenu editMenu;
    private final RaplaMenu viewMenu;
    private final RaplaMenu newMenu;
    private final RaplaMenu systemMenu;
    private final RaplaMenu extraMenu;
    private final RaplaMenu adminMenu;
    private final RaplaMenu importMenu;
    private final RaplaMenu exportMenu;
    @Inject public RaplaMenuBarContainer(RaplaResources i18n)
            throws RaplaInitializationException
    {
        menubar = new RaplaMenubar();
        systemMenu =  new RaplaMenu( InternMenus.FILE_MENU_ROLE.getId() );
        editMenu = new RaplaMenu( InternMenus.EDIT_MENU_ROLE.getId() );
        editMenu.add( new RaplaSeparator("EDIT_BEGIN"));
        editMenu.add( new RaplaSeparator("EDIT_END"));
        viewMenu = new RaplaMenu( InternMenus.VIEW_MENU_ROLE.getId() );
        extraMenu = new RaplaMenu( InternMenus.EXTRA_MENU_ROLE.getId() );

        newMenu = new RaplaMenu( InternMenus.NEW_MENU_ROLE.getId() );
        calendarSettings = new RaplaMenu( InternMenus.CALENDAR_SETTINGS.getId());
        adminMenu = new RaplaMenu( InternMenus.ADMIN_MENU_ROLE.getId() );
        importMenu = new RaplaMenu( InternMenus.IMPORT_MENU_ROLE.getId());
        exportMenu = new RaplaMenu( InternMenus.EXPORT_MENU_ROLE.getId());

        menubar.add(systemMenu);
        menubar.add(editMenu);
        menubar.add(viewMenu);
        menubar.add(extraMenu);

        systemMenu.setText(i18n.getString("file"));

        editMenu.setText(i18n.getString("edit"));

        exportMenu.setText(i18n.getString("export"));

        importMenu.setText(i18n.getString("import"));

        newMenu.setText(i18n.getString("new"));

        calendarSettings.setText(i18n.getString("calendar"));

        extraMenu.setText(i18n.getString("help"));

        adminMenu.setText(i18n.getString("admin"));

        viewMenu.setText(i18n.getString("view"));

        viewMenu.add(new RaplaSeparator("view_save"));

        /*
        if (getUser().isAdmin())
        {
            addPluginExtensions(adminMenuExt, adminMenu);
        }
        addPluginExtensions(importMenuExt, importMenu);
        addPluginExtensions(exportMenuExt, exportMenu);
        addPluginExtensions(helpMenuExt, extraMenu);
        addPluginExtensions(viewMenuExt, viewMenu);
        addPluginExtensions(editMenuExt, editMenu);
        */

        systemMenu.add(newMenu);
        systemMenu.add(calendarSettings);

        systemMenu.add(new JSeparator());

        systemMenu.add(exportMenu);
        systemMenu.add(importMenu);
        systemMenu.add(adminMenu);
    }

    public JMenuBar getMenubar()
    {
        return menubar;
    }

    public RaplaMenu getSettingsMenu()
    {
        return calendarSettings;
    }

    public RaplaMenu getEditMenu()
    {
        return editMenu;
    }

    public RaplaMenu getViewMenu()
    {
        return viewMenu;
    }

    public RaplaMenu getNewMenu()
    {
        return newMenu;
    }

    public RaplaMenu getSystemMenu()
    {
        return systemMenu;
    }

    public RaplaMenu getAdminMenu()
    {
        return adminMenu;
    }

    public RaplaMenu getExtraMenu()
    {
        return extraMenu;
    }

    public RaplaMenu getImportMenu()
    {
        return importMenu;
    }

    public RaplaMenu getExportMenu()
    {
        return exportMenu;
    }
}




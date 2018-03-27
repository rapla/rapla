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
package org.rapla.client.swing.internal.edit.fields;

import org.rapla.RaplaResources;
import org.rapla.client.swing.internal.edit.RaplaListEdit;
import org.rapla.client.swing.internal.edit.RaplaListEdit.RaplaListEditFactory;
import org.rapla.client.swing.internal.edit.fields.PermissionField.PermissionFieldFactory;
import org.rapla.client.swing.toolkit.EmptyLineBorder;
import org.rapla.entities.Category;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 *  @author Christopher Kohlhaas
 */
public class PermissionListField extends AbstractEditField implements EditFieldWithLayout
{
    JList permissionList = new JList();
    JPanel jPanel = new JPanel();
    PermissionField permissionField;
    private RaplaListEdit<Permission> listEdit;
    Listener listener = new Listener();
    PermissionContainer firstAllocatable;
    DefaultListModel model = new DefaultListModel();
    Permission selectedPermission = null;
    int selectedIndex = 0;
    Permission.AccessLevel defaultAccessLevel = null;

    List<Permission> notAllList = new ArrayList<>();

    public PermissionListField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, String fieldName,
            RaplaListEditFactory raplaListEditFactory, PermissionFieldFactory permissionFieldFactory) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        this.permissionField = permissionFieldFactory.create();
        super.setFieldName(fieldName);
        jPanel.setLayout(new BorderLayout());
        listEdit = raplaListEditFactory.create( permissionField.getComponent(), listener, false);
        jPanel.add(listEdit.getComponent(), BorderLayout.CENTER);

        jPanel.setBorder(BorderFactory.createTitledBorder(new EmptyLineBorder(), i18n.getString("permissions")));
        permissionField.addChangeListener(listener);
    }

    public JComponent getComponent()
    {
        return jPanel;
    }

    public EditFieldLayout getLayout()
    {
        EditFieldLayout layout = new EditFieldLayout();
        layout.setBlock(true);
        return layout;
    }

    public void mapTo(List<? extends PermissionContainer> list)
    {
        for (PermissionContainer allocatable : list)
        {
            for (Permission perm : new ArrayList<>(allocatable.getPermissionList()))
            {
                if (!model.contains(perm))
                {
                    allocatable.removePermission(perm);
                }
            }
            for (Permission perm : getPermissionList())
            {
                if (!hasPermission(allocatable, perm) && !isNotForAll(perm))
                {
                    allocatable.addPermission(((PermissionImpl) perm).clone());
                }
            }
        }
    }

    public Collection<Permission> getPermissionList()
    {
        Collection<Permission> result = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Enumeration<Permission> it = (Enumeration<Permission>) model.elements();
        while (it.hasMoreElements())
        {
            Permission perm = it.nextElement();
            result.add(perm);
        }
        return result;
    }

    private boolean hasPermission(PermissionContainer allocatable, Permission permission)
    {
        for (Permission perm : allocatable.getPermissionList())
        {
            if (perm.equals(permission))
            {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public void mapFrom(List<? extends PermissionContainer> list)
    {
        model.clear();
        firstAllocatable = list.size() > 0 ? list.get(0) : null;
        Set<Permission> permissions = new LinkedHashSet<>();
        boolean eventTypeList = false;
        for (PermissionContainer container : list)
        {
            Collection<Permission> permissionList = container.getPermissionList();
            for (Permission p : permissionList)
            {
                permissions.add(p);
            }

            if (container instanceof DynamicType)
            {
                eventTypeList = ((DynamicType) container)
                        .getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)
                        .equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            }
        }
        permissionField.setEventType(eventTypeList);

        Set<Permission> set = new LinkedHashSet<>();
        for (Permission perm : permissions)
        {
            Permission permissionClone = ((PermissionImpl) perm).clone();
            model.addElement(permissionClone);
            for (PermissionContainer allocatable : list)
            {
                Collection<Permission> asList = allocatable.getPermissionList();
                if (!asList.contains(permissionClone))
                {
                    set.add(permissionClone);
                }
            }
        }
        notAllList.clear();
        for (Permission perm : set)
        {
            notAllList.add(perm);
        }

        Category userGroup;
        try
        {
            userGroup = raplaFacade.getUserGroupsCategory();
        }
        catch (RaplaException e)
        {
            return;
        }

        listEdit.setListDimension(new Dimension(300, 90));
        listEdit.setMoveButtonVisible(false);
        listEdit.getList().setModel(model);
        listEdit.getList().setCellRenderer(new DefaultListCellRenderer()
        {
            private static final long serialVersionUID = 1L;

            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {

                Permission p = (Permission) value;
                if (p.getUser() != null)
                {
                    value = i18n.getString("user") + " " + p.getUser().getUsername();
                }
                else
                {
                    final Category group = p.getGroup();
                    if (group != null)
                    {
                        value = i18n.getString("group") + " " +
                                group.getPath( userGroup,i18n.getLocale());
                                //+ group.getName(i18n.getLocale());
                    }
                    else
                    {
                        value = i18n.getString("all_users");
                    }
                }
                value = (index + 1) + ") " + value;
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                Font f;

                if (isNotForAll(p))
                {
                    f = component.getFont().deriveFont(Font.ITALIC);
                }
                else
                {
                    f = component.getFont().deriveFont(Font.BOLD);
                }

                component.setFont(f);
                return component;
            }

        });

    }

    // Check if permission is in notAllList. We need to check references as the equals method could also match another permission
    private boolean isNotForAll(Permission p)
    {

        for (Permission perm : notAllList)
        {
            if (perm == p)
            {
                return true;
            }
        }
        return false;
    }

    private void removePermission()
    {
        try
        {
            listenersEnabled = false;
            for (Permission permission : listEdit.getSelectedValues())
            {
                model.removeElement(permission);
            }
            selectedIndex = -1;
            selectedPermission = null;
            listEdit.getList().requestFocus();
        }
        finally
        {
            listenersEnabled = true;
        }
    }

    @SuppressWarnings("unchecked")
    private void createPermission()
    {
        if (firstAllocatable == null)
        {
            return;
        }
        Permission permission = firstAllocatable.newPermission();
        if (defaultAccessLevel != null)
        {
            permission.setAccessLevel(defaultAccessLevel);
        }
        Permission.AccessLevel accessLevel = permission.getAccessLevel();
        Collection<Permission.AccessLevel> permissionLevels = permissionField.getPermissionLevels();
        if (!permissionLevels.contains(accessLevel))
        {
            permission.setAccessLevel(permissionLevels.iterator().next());
        }
        try
        {
            listenersEnabled = false;
            permissionField.setValue(permission);
            model.addElement(permission);
            JList list = listEdit.getList();
            selectedIndex = model.size() - 1;
            selectedPermission = permission;
            list.setSelectedIndex(selectedIndex);
        }
        finally
        {
            listenersEnabled = true;
        }
    }

    boolean listenersEnabled = true;

    class Listener implements ActionListener, ChangeListener
    {
        public void actionPerformed(ActionEvent evt)
        {
            if (!listenersEnabled)
            {
                return;
            }
            if (evt.getActionCommand().equals("remove"))
            {
                removePermission();
                fireContentChanged();
            }
            else if (evt.getActionCommand().equals("new"))
            {
                createPermission();
                fireContentChanged();
            }
            else if (evt.getActionCommand().equals("edit"))
            {
                // buffer selected Permission
                selectedPermission = (Permission) listEdit.getList().getSelectedValue();
                selectedIndex = listEdit.getList().getSelectedIndex();
                // commit common Permissions (like the selected one) for
                // processing
                permissionField.setValue(selectedPermission);
            }
        }

        @SuppressWarnings("unchecked")
        public void stateChanged(ChangeEvent evt)
        {
            if (!listenersEnabled)
            {
                return;
            }
            // set processed selected Permission in the list
            if (selectedPermission == null)
            {
                return;
            }
            model.set(selectedIndex, selectedPermission);
            // remove permission from notAllList we need to check references as the equals method could also match another permission
            Iterator<Permission> it = notAllList.iterator();
            while (it.hasNext())
            {
                Permission next = it.next();
                if (next == selectedPermission)
                {
                    it.remove();
                }
            }
            fireContentChanged();
        }
    }

    public void setPermissionLevels(Permission.AccessLevel... permissionLevels)
    {
        this.permissionField.setPermissionLevels(permissionLevels);
    }

    public void setDefaultAccessLevel(Permission.AccessLevel defaultAccessLevel)
    {
        this.defaultAccessLevel = defaultAccessLevel;
    }

    public void setUserSelectVisible(boolean userSelectVisible)
    {
        permissionField.setUserVisible(userSelectVisible);
    }

    public Permission.AccessLevel getDefaultAccessLevel()
    {
        return defaultAccessLevel;
    }

    @Singleton
    public static class PermissionListFieldFactory
    {
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final RaplaListEditFactory raplaListEditFactory;
        private final PermissionFieldFactory permissionFieldFactory;

        @Inject
        public PermissionListFieldFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
                RaplaListEditFactory raplaListEditFactory, PermissionFieldFactory permissionFieldFactory)
        {
            super();
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.raplaListEditFactory = raplaListEditFactory;
            this.permissionFieldFactory = permissionFieldFactory;
        }

        public PermissionListField create(final String fieldName) throws RaplaException
        {
            return new PermissionListField(facade, i18n, raplaLocale, logger, fieldName, raplaListEditFactory, permissionFieldFactory);
        }
    }

}



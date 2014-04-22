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
package org.rapla.gui.internal.edit.fields;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.internal.edit.RaplaListEdit;
import org.rapla.gui.toolkit.EmptyLineBorder;
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
	Allocatable firstAllocatable;
	DefaultListModel model = new DefaultListModel();
	Permission selectedPermission = null;
	int selectedIndex = 0;
	
	List<Permission> notAllList = new ArrayList<Permission>();
	public PermissionListField(RaplaContext context, String fieldName) throws RaplaException {
		super(context);
		permissionField = new PermissionField(context);
		super.setFieldName(fieldName);
		jPanel.setLayout(new BorderLayout());
		listEdit = new RaplaListEdit<Permission>(getI18n(), permissionField.getComponent(),	listener);
		jPanel.add(listEdit.getComponent(), BorderLayout.CENTER);
		
		jPanel.setBorder(BorderFactory.createTitledBorder(new EmptyLineBorder(), getString("permissions")));
		permissionField.addChangeListener(listener);
	}

	public JComponent getComponent() {
		return jPanel;
	}

	public EditFieldLayout getLayout()
	{
	    EditFieldLayout layout = new EditFieldLayout();
	    return layout;
	}
	
	public void mapTo(List<Allocatable> list) {
		for (Allocatable allocatable :list)
		{
	    	for (Permission perm : allocatable.getPermissions())
	    	{
	    		if (!model.contains( perm) )
	    		{
	    			allocatable.removePermission(perm);
	    		}
	    	}
	    	@SuppressWarnings({ "unchecked", "cast" })
			Enumeration<Permission> it = (Enumeration<Permission>) model.elements();
	    	while (it.hasMoreElements())
	    	{
	    		Permission perm= it.nextElement();
	    		if ( !hasPermission(allocatable, perm) && !isNotForAll( perm))
	    		{
	    			allocatable.addPermission( perm);
	    		}
	    	}
		}
    }
		

	private boolean hasPermission(Allocatable allocatable, Permission permission) {
		for (Permission perm: allocatable.getPermissions())
		{
			if  (perm.equals( permission))
			{
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public void mapFrom(List<Allocatable> list) {
		model.clear();
		firstAllocatable = list.size() > 0 ? list.get(0) : null;
		Set<Permission> permissions = new LinkedHashSet<Permission>();
		for (Allocatable allocatable :list)
		{
			List<Permission> permissionList = Arrays.asList(allocatable.getPermissions());
			permissions.addAll(permissionList);
		}

		Set<Permission> set = new LinkedHashSet<Permission>();
		for (Permission perm : permissions) {
			model.addElement(perm);
			 for (Allocatable allocatable:list)
			 {
				List<Permission> asList = Arrays.asList(allocatable.getPermissions());
				if (!asList.contains(perm))
				{
					set.add( perm);
				}
			}
		}
		notAllList.clear();
		for (Permission perm : set) 
		{
			notAllList.add(perm);
		}
		
		listEdit.setListDimension(new Dimension(210, 90));
		listEdit.setMoveButtonVisible(false);
		listEdit.getList().setModel(model);
		listEdit.getList().setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(JList list,
					Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				Permission p = (Permission) value;
				if (p.getUser() != null) {
					value = getString("user") + " " + p.getUser().getUsername();
				} else if (p.getGroup() != null) {
					value = getString("group") + " "
							+ p.getGroup().getName(getI18n().getLocale());
				} else {
					value = getString("all_users");
				}
				value = (index + 1) + ") " + value;
				Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				Font f;
				
				if (isNotForAll(  p))
				{
					f =component.getFont().deriveFont(Font.ITALIC);
				}
				else
				{
					f =component.getFont().deriveFont(Font.BOLD);
				}
				
				component.setFont(f);
				return component;
			}

			
		});
		

	}
	
	// Check if permission is in notAllList. We need to check references as the equals method could also match another permission 
	private boolean isNotForAll(	Permission p) {
		
		for (Permission perm: notAllList)
		{
			if ( perm == p)
			{
				return true;
			}
		}
		return false;
	}
	private void removePermission() {
		for (Permission permission:listEdit.getSelectedValues())
		{
			model.removeElement(permission);
		}
		listEdit.getList().requestFocus();
	}

	@SuppressWarnings("unchecked")
	private void createPermission() {
	    if ( firstAllocatable == null)
	    {
	        return;
	    }
	    Permission permission = firstAllocatable.newPermission();
		model.addElement(permission);
	}

	class Listener implements ActionListener, ChangeListener {
		public void actionPerformed(ActionEvent evt) {
			if (evt.getActionCommand().equals("remove")) {
				removePermission();
			} else if (evt.getActionCommand().equals("new")) {
				createPermission();
			} else if (evt.getActionCommand().equals("edit")) {
				// buffer selected Permission
				selectedPermission = (Permission) listEdit.getList().getSelectedValue();
				selectedIndex = listEdit.getList().getSelectedIndex();
				// commit common Permissions (like the selected one) for
				// processing
				permissionField.setValue(selectedPermission);
			}
		}

		@SuppressWarnings("unchecked")
		public void stateChanged(ChangeEvent evt) {
			// set processed selected Permission in the list
			model.set(selectedIndex, selectedPermission);
			// remove permission from notAllList we need to check references as the equals method could also match another permission 
			Iterator<Permission> it = notAllList.iterator();
			while (it.hasNext())
			{
				Permission next = it.next();
				if ( next == selectedPermission )
				{
					it.remove();
				}
			}
		}
	}



}



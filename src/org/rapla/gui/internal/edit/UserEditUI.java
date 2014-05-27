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
package org.rapla.gui.internal.edit;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.EditField;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.internal.edit.fields.AbstractEditField;
import org.rapla.gui.internal.edit.fields.BooleanField;
import org.rapla.gui.internal.edit.fields.GroupListField;
import org.rapla.gui.internal.edit.fields.TextField;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.gui.toolkit.RaplaTree;

/****************************************************************
 * This is the controller-class for the User-Edit-Panel         *
 ****************************************************************/

/*User
  1. username, string
  2. name,string
  3. email,string,
  4. isadmin,boolean
*/

class UserEditUI  extends AbstractEditUI<User> {
    TextField usernameField;
    PersonSelectField personSelect;
    TextField nameField;
    TextField emailField;
    AdminBooleanField adminField;
    GroupListField groupField;
    /**
     * @param context
     * @throws RaplaException
     */
    public UserEditUI(RaplaContext context) throws RaplaException {
        super(context);
        List<EditField> fields = new ArrayList<EditField>();
        usernameField = new TextField(context,getString("username"));
        fields.add(usernameField);
        personSelect = new PersonSelectField(context);
        fields.add(personSelect);
        nameField = new TextField(context,getString("name"));
        fields.add(nameField);
        emailField = new TextField(context,getString("email"));
        fields.add(emailField);
        adminField = new AdminBooleanField(context,getString("admin"),getUser());
        fields.add(adminField);
        groupField = new GroupListField(context);
        fields.add(groupField);
        setFields(fields);
    }
    
    class AdminBooleanField extends BooleanField implements ChangeListener {
        User user;
        public AdminBooleanField(RaplaContext sm, String fieldName, User user)  {
            super(sm, fieldName);
            this.user = user;
        }
        
		public void stateChanged(ChangeEvent e) {
		}
		
        public void actionPerformed(ActionEvent evt) {
	        if(evt.getActionCommand().equals(getString("no"))) {
	        	try {
					if(!isOneAdmin()) 
					{
						showWarning(getString("error.no_admin"), getComponent());
						setValue(true);
					}
				} 
	        	catch (RaplaException ex) 
				{
	        		showException(ex, getComponent());
				}
			}  
	        return;
		}
		
		private Boolean isOneAdmin() throws RaplaException {
	        User[] userList = getQuery().getUsers();
	        if (objectList.size() != 1)
	        {
	            return true;
	        }
	        User user2 = objectList.get(0);
	        for (final User user: userList) 
	        {
                if(!user.equals(user2) && user.isAdmin())
	        	{
                    return true;
		        }
	        }
	        return false;
	    }
    }
    
    class PersonSelectField extends AbstractEditField implements ChangeListener, ActionListener {
        User user;
       
        JPanel panel = new JPanel();
        JToolBar toolbar = new JToolBar();

        RaplaButton newButton  = new RaplaButton(RaplaButton.SMALL);
        RaplaButton removeButton  = new RaplaButton(RaplaButton.SMALL);
        
        /**
         * @param sm
         * @throws RaplaException
         */
        public PersonSelectField(RaplaContext sm) throws RaplaException {
            super(sm);
            setFieldName( getString("person"));
            final Category rootCategory = getQuery().getUserGroupsCategory();
            if ( rootCategory == null )
                return;
            toolbar.add( newButton  );
            toolbar.add( removeButton  );
            toolbar.setFloatable( false );
            panel.setLayout( new BorderLayout() );
            panel.add( toolbar, BorderLayout.NORTH );
            newButton.addActionListener( this );
            removeButton.addActionListener( this );
            removeButton.setText( getString("remove") );
            removeButton.setIcon( getIcon( "icon.remove" ) );
            newButton.setText( getString("bind_with_person") );
            newButton.setIcon( getIcon( "icon.new" ) );

        }

        private void updateButton() {
            final boolean personSet = user != null && user.getPerson() != null;
            removeButton.setEnabled( personSet) ;
            newButton.setEnabled( !personSet) ;
            
            nameField.getComponent().setEnabled( !personSet);
            emailField.getComponent().setEnabled( !personSet);
      
        }

        public JComponent getComponent() {
            return panel;
        }

        public String getName()
        {
            return getString("bind_with_person");
        }
        
        public void setUser(User o){
           user = o;
           updateButton();
        }


        public void actionPerformed(ActionEvent evt) {
            if ( evt.getSource() ==  newButton)
            {
                try {
                    showAddDialog();
                } catch (RaplaException ex) {
                    showException(ex,newButton);
                }
            }
            
            if ( evt.getSource() ==  removeButton)
            {
                try {
					user.setPerson( null );
	                user.setEmail( null );
	                user.setName(null);
	                nameField.setValue( user.getName());
	                emailField.setValue( user.getEmail());
	                updateButton();
				} catch (RaplaException ex) {
					showException(ex, getComponent());
				}
            }
               
        }

        public void stateChanged(ChangeEvent evt) {
        }
        
        private void showAddDialog() throws RaplaException {
            final DialogUI dialog;
            RaplaTree treeSelection = new RaplaTree();
            treeSelection.setMultiSelect(true);
            final TreeFactory treeFactory = getTreeFactory();
            treeSelection.getTree().setCellRenderer(treeFactory.createRenderer());

            final DynamicType[] personTypes = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
            List<ClassificationFilter> filters = new ArrayList<ClassificationFilter>();
            for (DynamicType personType: personTypes)
            {
                if ( personType.getAttribute("email") != null)
                {
                    final ClassificationFilter filter = personType.newClassificationFilter();
                    filters.add( filter);
                }
            }
            final Allocatable[] allocatables = getQuery().getAllocatables(filters.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY));
            List<Allocatable> allocatablesWithEmail = new ArrayList<Allocatable>();
            for ( Allocatable a: allocatables)
            {
                final Classification classification = a.getClassification();
                final Attribute attribute = classification.getAttribute("email");
                if ( attribute != null)
                {
	                final String email = (String)classification.getValue(attribute);
	                if (email != null && email.length() > 0)
	                {
	                    allocatablesWithEmail.add( a );
	                }
                }
            }
            final Allocatable[] allocatableArray = allocatablesWithEmail.toArray(Allocatable.ALLOCATABLE_ARRAY);
            treeSelection.exchangeTreeModel(treeFactory.createClassifiableModel(allocatableArray,true));
            treeSelection.setMinimumSize(new java.awt.Dimension(300, 200));
            treeSelection.setPreferredSize(new java.awt.Dimension(400, 260));
            
           
            dialog = DialogUI.create(
                    getContext()
                    ,getComponent()
                    ,true
                    ,treeSelection
                    ,new String[] { getString("apply"),getString("cancel")});
            final JTree tree = treeSelection.getTree();
            tree.addMouseListener(new MouseAdapter() {
                // End dialog when a leaf is double clicked
                public void mousePressed(MouseEvent e) {
                    TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                    if (selPath != null && e.getClickCount() == 2) {
                        final Object lastPathComponent = selPath.getLastPathComponent();
                        if (((TreeNode) lastPathComponent).isLeaf() )
                            dialog.getButton(0).doClick();
                        }
                    else if (selPath != null && e.getClickCount() == 1) {
                        final Object lastPathComponent = selPath.getLastPathComponent();
                        if (((TreeNode) lastPathComponent).isLeaf() ) 
                            dialog.getButton(0).setEnabled(true);
                        else
                            dialog.getButton(0).setEnabled(false);                       
                    }
                }
            });
            dialog.setTitle(getName());
            dialog.start();
            if (dialog.getSelectedIndex() == 0) {
                Iterator<?> it = treeSelection.getSelectedElements().iterator();
                while (it.hasNext()) {
                    user.setPerson((Allocatable) it.next());
                    nameField.setValue( user.getName());
                    emailField.setValue( user.getEmail());
                    updateButton();
                }
            }
        }

    }
    
    final private TreeFactory getTreeFactory() {
        return getService(TreeFactory.class);
    }

    
 
 	
    @Override
    public void mapToObjects() throws RaplaException {
        if (objectList == null)
            return;
	    if (objectList.size() == 1 )
        {
	        User user = objectList.iterator().next();
	        user.setName(nameField.getValue());
	        user.setEmail( emailField.getValue());
	        user.setUsername( usernameField.getValue());
	        user.setAdmin( adminField.getValue());
	        // personselect stays in sync
        }
	    groupField.mapTo( objectList);
    }


 // overwriting of the method by AbstractEditUI
    // goal: deactivation of the username-field in case of processing multiple
    // objects, to avoid that usernames (identifier) are processed at the same
    // time
    // => multiple users would have the same username
    @Override
    protected void mapFromObjects() throws RaplaException {
        boolean multiedit = objectList.size() > 1;
        if (objectList.size() == 1 )
        {
            User user = objectList.iterator().next();
            nameField.setValue(user.getName());
            emailField.setValue(user.getEmail());
            usernameField.setValue(user.getUsername( ));
            adminField.setValue( user.isAdmin( ));
            personSelect.setUser( user);
        }
        groupField.mapFrom( objectList);

        for (EditField field:fields) {

            // deactivation of the all fields except group for multiple objects
            if (multiedit && !(field instanceof GroupListField ))
            {
                field.getComponent().setEnabled(false);
                field.getComponent().setVisible(false);
            }
        }
    }
}

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
package org.rapla.client.swing.internal.edit;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.EditField;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.fields.AbstractEditField;
import org.rapla.client.swing.internal.edit.fields.BooleanField;
import org.rapla.client.swing.internal.edit.fields.GroupListField;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.client.swing.internal.view.RaplaSwingTreeModel;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/****************************************************************
 * This is the controller-class for the User-Edit-Panel         *
 ****************************************************************/

/*User
  1. username, string
  2. name,string
  3. email,string,
  4. isadmin,boolean
*/

@Extension(provides = EditComponent.class, id="org.rapla.entities.User")
public class UserEditUI  extends AbstractEditUI<User> {
    TextField usernameField;
    PersonSelectField personSelect;
    TextField nameField;
    TextField emailField;
    AdminBooleanField adminField;
    GroupListField groupField;
    private final TreeFactory treeFactory;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final TreeCellRenderer treeCellRenderer;
    /**
     * @throws RaplaException
     */
    @Inject
    public UserEditUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, DialogUiFactoryInterface dialogUiFactory, GroupListField groupField, TextFieldFactory textFieldFactory, TreeCellRenderer treeCellRenderer) throws
            RaplaInitializationException {
        super(facade, i18n, raplaLocale, logger);
        this.treeFactory = treeFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.treeCellRenderer = treeCellRenderer;
        List<EditField> fields = new ArrayList<>();
        usernameField = textFieldFactory.create(getString("username"));
        fields.add(usernameField);
        personSelect = new PersonSelectField(facade, i18n, raplaLocale, logger);
        fields.add(personSelect);
        nameField = textFieldFactory.create(getString("name"));
        fields.add(nameField);
        emailField = textFieldFactory.create(getString("email"));
        fields.add(emailField);
        adminField = new AdminBooleanField(facade, i18n, raplaLocale, logger, getString("admin"));
        fields.add(adminField);
        this.groupField = groupField;
        fields.add(this.groupField);
        setFields(fields);
    }

    public void setIcon(JButton button, I18nIcon icon)
    {
        button.setIcon(RaplaImages.getIcon( icon));
    }
    
    class AdminBooleanField extends BooleanField implements ChangeListener {
        User user;
        public AdminBooleanField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, String fieldName) throws RaplaInitializationException  {
            super(facade, i18n, raplaLocale, logger, fieldName);
            try
            {
                this.user = facade.getUser();
                setEditable( user.isAdmin());
            }
            catch (RaplaException e)
            {
                throw new RaplaInitializationException(e);
            }
        }
        
		public void stateChanged(ChangeEvent e) {
		}
		
        public void actionPerformed(ActionEvent evt) {
	        if(evt.getActionCommand().equals(getString("no"))) {
	        	try {
					if(!isOneAdmin()) 
					{
					    dialogUiFactory.showWarning(getString("error.no_admin"), new SwingPopupContext(getComponent(), null));
						setValue(true);
					}
				} 
	        	catch (RaplaException ex) 
				{
	        	    dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
				}
			}  
	        return;
		}
		
		private Boolean isOneAdmin() throws RaplaException {
            if ( !user.isAdmin())
            {
                return true;
            }
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
        
        public PersonSelectField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) throws RaplaInitializationException {
            super(facade, i18n, raplaLocale, logger);
            setFieldName( getString("person"));
            final Category rootCategory;
            try
            {
                rootCategory = facade.getRaplaFacade().getUserGroupsCategory();
            }
            catch (RaplaException e)
            {
                throw new RaplaInitializationException(e);
            }
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
            setIcon( removeButton,i18n.getIcon( "icon.remove" ) );
            newButton.setText( getString("bind_with_person") );
            setIcon(newButton, i18n.getIcon( "icon.new" ) );

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
                    dialogUiFactory.showException(ex,new SwingPopupContext(newButton, null));
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
				    dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
				}
            }
               
        }

        public void stateChanged(ChangeEvent evt) {
        }
        
        private void showAddDialog() throws RaplaException {
            final DialogInterface dialog;
            RaplaTree treeSelection = new RaplaTree();
            treeSelection.setMultiSelect(true);
            treeSelection.getTree().setCellRenderer(treeCellRenderer);

            final DynamicType[] personTypes = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
            List<ClassificationFilter> filters = new ArrayList<>();
            for (DynamicType personType: personTypes)
            {
                if ( personType.getAttribute("email") != null)
                {
                    final ClassificationFilter filter = personType.newClassificationFilter();
                    filters.add( filter);
                }
            }
            final Allocatable[] allocatables = getQuery().getAllocatablesWithFilter(filters.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY));
            List<Allocatable> allocatablesWithEmail = new ArrayList<>();
            for ( Allocatable a: allocatables)
            {
                final Classification classification = a.getClassification();
                final Attribute attribute = classification.getAttribute("email");
                if ( attribute != null)
                {
	                final String email = (String)classification.getValueForAttribute(attribute);
	                if (email != null && email.length() > 0)
	                {
	                    allocatablesWithEmail.add( a );
	                }
                }
            }
            final Allocatable[] allocatableArray = allocatablesWithEmail.toArray(Allocatable.ALLOCATABLE_ARRAY);
            final RaplaTreeNode classifiableModel = treeFactory.createClassifiableModel(allocatableArray, true);
            treeSelection.exchangeTreeModel(new RaplaSwingTreeModel(classifiableModel));
            treeSelection.setMinimumSize(new java.awt.Dimension(300, 200));
            treeSelection.setPreferredSize(new java.awt.Dimension(400, 260));
            
           
            dialog = dialogUiFactory.createContentDialog(
                    new SwingPopupContext(getComponent(), null)
                    ,
                    treeSelection
                    ,new String[] { getString("apply"),getString("cancel")});
            final JTree tree = treeSelection.getTree();
            tree.addMouseListener(new MouseAdapter() {
                // End dialog when a leaf is double clicked
                public void mousePressed(MouseEvent e) {
                    TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                    if (selPath != null && e.getClickCount() == 2) {
                        final Object lastPathComponent = selPath.getLastPathComponent();
                        if (((TreeNode) lastPathComponent).isLeaf() )
                            dialog.getAction(0).execute();
                        }
                    else if (selPath != null && e.getClickCount() == 1) {
                        final Object lastPathComponent = selPath.getLastPathComponent();
                        if (((TreeNode) lastPathComponent).isLeaf() ) 
                            dialog.getAction(0).setEnabled(true);
                        else
                            dialog.getAction(0).setEnabled(false);                       
                    }
                }
            });
            dialog.setTitle(getName());
            dialog.start(true).execOn(SwingUtilities::invokeLater).thenAccept( index->
            {
                if (index == 0) {
                    Iterator<?> it = treeSelection.getSelectedElements().iterator();
                    while (it.hasNext()) {
                        user.setPerson((Allocatable) it.next());
                        nameField.setValue(user.getName());
                        emailField.setValue(user.getEmail());
                        updateButton();
                    }
                }
            });
        }

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

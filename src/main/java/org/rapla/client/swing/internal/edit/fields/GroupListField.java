package org.rapla.client.swing.internal.edit.fields;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class GroupListField extends AbstractEditField implements ChangeListener, ActionListener, EditFieldWithLayout {
    DefaultListModel model = new DefaultListModel();

    JPanel panel = new JPanel();
    JToolBar toolbar = new JToolBar();

    CategorySelectField newCategory;
    RaplaButton removeButton = new RaplaButton(RaplaButton.SMALL);
    RaplaButton newButton  = new RaplaButton(RaplaButton.SMALL);
    JList list = new JList();
    Set<Category> notAllList = new HashSet<>();

    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject
    public GroupListField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory,  DialogUiFactoryInterface dialogUiFactory) throws
            RaplaInitializationException {
        super(facade, i18n, raplaLocale, logger);
        this.dialogUiFactory = dialogUiFactory;
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
        newCategory = new CategorySelectField(facade, i18n, raplaLocale, logger, treeFactory,  dialogUiFactory, rootCategory );
        newCategory.setUseNull( false);
        newCategory.setMultipleSelectionPossible( true);
        toolbar.add( newButton  );
        toolbar.add( removeButton );
        toolbar.setFloatable( false );
        panel.setLayout( new BorderLayout() );
        panel.add( toolbar, BorderLayout.NORTH );
        final JScrollPane jScrollPane = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jScrollPane.setPreferredSize( new Dimension( 300, 150 ) );
        panel.add( jScrollPane, BorderLayout.CENTER );
        newButton.setText( i18n.getString( "group" ) + " " + i18n.getString( "add" ) );
        removeButton.setText( i18n.getString( "group" ) + " " + i18n.getString( "remove" ) );
        setIcon( newButton,i18n.getIcon( "icon.new" ) );
        setIcon( removeButton,i18n.getIcon( "icon.remove" ) );
        newCategory.addChangeListener( this );
        newButton.addActionListener( this );
        removeButton.addActionListener( this );

        DefaultListCellRenderer cellRenderer = new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            public Component getListCellRendererComponent(JList list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus)
                {
                	Category category = (Category) value;
            		if ( value != null ) {
                        value = category.getPath( rootCategory
                                                  , i18n.getLocale());
                    }
                    Component component = super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                    if (notAllList.contains( category))
                    {
                    	Font f =component.getFont().deriveFont(Font.ITALIC);
                    	component.setFont(f);
                    }
                    return component;
                }
            };
		setRenderer(cellRenderer);
    }

	@SuppressWarnings("unchecked")
	private void setRenderer(DefaultListCellRenderer cellRenderer) {
		list.setCellRenderer(cellRenderer );
	}

    public JComponent getComponent() {
        return panel;
    }

    public void setIcon(JButton button, I18nIcon icon)
    {
        button.setIcon(RaplaImages.getIcon( icon));
    }

    @Override
    public EditFieldLayout getLayout() {
        EditFieldLayout layout = new EditFieldLayout();
        layout.setBlock( true);
        layout.setVariableSized( false);
        return layout;
    }

    public void mapFrom(List<User> users) {

    	Set<Category> categories = new LinkedHashSet<>();
		// determination of the common categories/user groups
		for (User user:users) {
			categories.addAll(user.getGroupList());
		}
		
		Set<Category> notAll = new LinkedHashSet<>();
		for ( Category group: categories)
		{
			for (User user:users) 
			{
				if (!user.belongsTo( group))
				{
					notAll.add(group);
				}
			}
		}
		mapFromList(categories,notAll);
	}
    
  
    public void mapToList(Collection<Category> groups)  {
    	groups.clear();
    	@SuppressWarnings({ "unchecked", "cast" })
		Enumeration<Category> it = (Enumeration<Category>) model.elements();
    	while (it.hasMoreElements())
    	{
    		Category cat= it.nextElement();
    		groups.add( cat);
    	}
    }
    
    public void mapFromList(Collection<Category> groups) {
    	mapFromList(groups, new HashSet<>());
    }
    
	@SuppressWarnings("unchecked")
	private void mapFromList(Collection<Category> groups,Set<Category> notToAll) {
		model.clear();
		this.notAllList = notToAll;
        for ( Category cat:groups) {
            model.addElement( cat );
        }
        list.setModel(model);
	}

    public void mapTo(List<User> users)  {
		for (User user:users)
		{
	    	for (Category cat : user.getGroupList())
	    	{
	    		if (!model.contains( cat) && !notAllList.contains( cat))
	    		{
	    			user.removeGroup( cat);
	    		}
	    	}
	    	@SuppressWarnings({ "unchecked", "cast" })
			Enumeration<Category> it = (Enumeration<Category>) model.elements();
	    	while (it.hasMoreElements())
	    	{
	    		Category cat= it.nextElement();
	    		if ( !user.getGroupList().contains(cat) && !notAllList.contains( cat))
	    		{
	    			user.addGroup( cat);
	    		}
	    	}
		}
    }
    
    
    public void actionPerformed(ActionEvent evt) {
        if ( evt.getSource() ==  newButton)
        {
            try {
            	newCategory.setValue( null );
                newCategory.showDialog(newButton);
            } catch (RaplaException ex) {
                dialogUiFactory.showException(ex,new SwingPopupContext(newButton, null));
            }
        }
        if ( evt.getSource() ==  removeButton)
        {
            @SuppressWarnings("deprecation")
			Object[] selectedValues = list.getSelectedValues();
			for ( Object value: selectedValues)
            {
                Category group = (Category) value;
                if (group != null) {
                    model.removeElement( group );
                    notAllList.remove( group);
                    fireContentChanged();
                }
            }
        }
        
    }
    
    @SuppressWarnings("unchecked")
	public void stateChanged(ChangeEvent evt) {
        Collection<Category> newGroup = newCategory.getValues();
        
        for ( Category group:newGroup)
        {
	        notAllList.remove( group);
	        if ( ! model.contains( group ) ) {
	            model.addElement( group );
	        }
        }
        fireContentChanged();
        list.repaint();
    }


}
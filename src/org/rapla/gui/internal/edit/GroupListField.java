package org.rapla.gui.internal.edit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.toolkit.RaplaButton;


public class GroupListField extends AbstractEditField implements ChangeListener, ActionListener {
    DefaultListModel model = new DefaultListModel();

    JPanel panel = new JPanel();
    JToolBar toolbar = new JToolBar();

    CategorySelectField newCategory;
    RaplaButton removeButton = new RaplaButton(RaplaButton.SMALL);
    RaplaButton newButton  = new RaplaButton(RaplaButton.SMALL);
    JList list = new JList();
    Set<Category> notAllList = new HashSet<Category>();
    /**
     * @param sm
     * @throws RaplaException
     */
    public GroupListField(RaplaContext sm) throws RaplaException {
        super(sm);
    	final Category rootCategory = getQuery().getUserGroupsCategory();
        if ( rootCategory == null )
            return;
        newCategory = new CategorySelectField(sm,"group", rootCategory );
        newCategory.setUseNull( false);
        toolbar.add( newButton  );
        toolbar.add( removeButton );
        toolbar.setFloatable( false );
        panel.setLayout( new BorderLayout() );
        panel.add( toolbar, BorderLayout.NORTH );
        final JScrollPane jScrollPane = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jScrollPane.setPreferredSize( new Dimension( 300, 150 ) );
        panel.add( jScrollPane, BorderLayout.CENTER );
        newButton.setText( getString( "group" ) + " " + getString( "add" ) );
        removeButton.setText( getString( "group" ) + " " + getString( "remove" ) );
        newButton.setIcon( getIcon( "icon.new" ) );
        removeButton.setIcon( getIcon( "icon.remove" ) );
        newCategory.addChangeListener( this );
        newButton.addActionListener( this );
        removeButton.addActionListener( this );

        list.setCellRenderer(new DefaultListCellRenderer() {
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
                                                  , getI18n().getLocale());
                    }
                    Component component = super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                    if (notAllList.contains( category))
                    {
                    	Font f =component.getFont().deriveFont(Font.ITALIC);
                    	component.setFont(f);
                    }
                    return component;
                }
            }
            );
    }

    public JComponent getComponent() {
        return panel;
    }

    public boolean isBlock() {
        return true;
    }

    public boolean isVariableSized() {
        return false;
    }
    
    public void mapFrom(List<User> users) {

    	Set<Category> categories = new LinkedHashSet<Category>();
		// determination of the common categories/user groups
		for (User user:users) {
			categories.addAll(Arrays.asList(user.getGroups()));
		}
		
		Set<Category> notAll = new LinkedHashSet<Category>();
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
    	mapFromList(groups, new HashSet<Category>());
    }
    
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
	    	for (Category cat : user.getGroups())
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
	    		if ( !user.belongsTo( cat) && !notAllList.contains( cat))
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
                showException(ex,newButton);
            }
        }
        if ( evt.getSource() ==  removeButton)
        {
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
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.entities.CategoryAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.EditComponent;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.internal.edit.fields.MultiLanguageField;
import org.rapla.gui.internal.edit.fields.TextField;
import org.rapla.gui.internal.view.TreeFactoryImpl.NamedNode;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.gui.toolkit.RaplaTree;

/**
 *  @author Christopher Kohlhaas
 */
public class CategoryEditUI extends RaplaGUIComponent
    implements
    EditComponent<Category>
{
    JPanel panel = new JPanel();

    JPanel toolbar = new JPanel();
    RaplaButton newButton = new RaplaButton();
    RaplaButton newSubButton = new RaplaButton();
    RaplaButton removeButton = new RaplaButton();
    RaplaArrowButton moveUpButton = new RaplaArrowButton('^', 25);
    RaplaArrowButton moveDownButton = new RaplaArrowButton('v', 25);

    Category rootCategory;
    CategoryDetail detailPanel;
    RaplaTreeEdit treeEdit;
    TreeModel model;
    boolean editKeys = true;
    Listener listener = new Listener();
    TreeCellRenderer iconRenderer;
    boolean createNew;

    public CategoryEditUI(RaplaContext context, boolean createNew)  {
        super( context);
        this.createNew = createNew;
        detailPanel = new CategoryDetail(context);
        panel.setPreferredSize( new Dimension( 690,350 ) );
        treeEdit = new RaplaTreeEdit( getI18n(),detailPanel.getComponent(), listener );
        treeEdit.setListDimension( new Dimension( 250,100 ) );
        toolbar.setLayout( new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.add(newButton);
        toolbar.add(newSubButton);
        toolbar.add( Box.createHorizontalStrut( 5 ));
        toolbar.add(removeButton);
        toolbar.add( Box.createHorizontalStrut( 5 ));
        toolbar.add(moveUpButton);
        toolbar.add(moveDownButton);
        panel.setLayout( new BorderLayout() );
        panel.add( toolbar, BorderLayout.NORTH );
        panel.add( treeEdit.getComponent(), BorderLayout.CENTER );

        newButton.addActionListener(listener);
        newSubButton.addActionListener(listener);
        removeButton.addActionListener(listener);
        moveUpButton.addActionListener( listener );
        moveDownButton.addActionListener( listener );
        iconRenderer = getTreeFactory().createRenderer();
        treeEdit.getTree().setCellRenderer(  new TreeCellRenderer() {
            public Component getTreeCellRendererComponent(JTree tree
                    ,Object value
                    ,boolean sel
                    ,boolean expanded
                    ,boolean leaf
                    ,int row
                    ,boolean hasFocus
            ) {
                if ( value instanceof NamedNode) {
                    
                	Category c = (Category) ((NamedNode)value).getUserObject();
                    value = c.getName(getRaplaLocale().getLocale());
                    if (editKeys) {
                        value = "{" + c.getKey() + "} " + value;
                    }
                }
                return iconRenderer.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus );
            }
        });
        newButton.setText( getString("new_category") );
        newButton.setIcon( getIcon("icon.new"));
        newSubButton.setText( getString("new_sub-category") );
        newSubButton.setIcon( getIcon("icon.new") );
        removeButton.setText( getString("delete") );
        removeButton.setIcon( getIcon("icon.delete") );

        detailPanel.addChangeListener( listener );
        detailPanel.setEditKeys( editKeys );
    }

    final private TreeFactory getTreeFactory() {
        return getService(TreeFactory.class);
    }


    class Listener implements ActionListener,ChangeListener {
        public void actionPerformed(ActionEvent evt) {
            try {
                if ( evt.getSource() == newButton ) {
                    createCategory( false );
                } else if ( evt.getSource() == newSubButton ) {
                    createCategory( true );
                } else if ( evt.getSource() == removeButton ) {
                    removeCategory();
                } else if ( evt.getSource() == moveUpButton ) {
                    moveCategory( -1);
                } else if ( evt.getSource() == moveDownButton ) {
                    moveCategory( 1);
                } else if (evt.getActionCommand().equals("edit")) {
                    Category category = (Category) treeEdit.getSelectedValue();
                    detailPanel.mapFrom( category );
                }
            } catch (RaplaException ex) {
                showException(ex, getComponent());
            }
        }
        public void stateChanged(ChangeEvent e) {
            try {
                confirmEdits();
            } catch (RaplaException ex) {
                showException(ex, getComponent());
            }
        }
    }

    public JComponent getComponent() {
        return panel;
    }

    public int getSelectedIndex() {
        return treeEdit.getSelectedIndex();
    }

    public void setObjects(List<Category> o) throws RaplaException {
        this.rootCategory = o.get(0);
        updateModel();
    }
    
	public void processCreateNew() throws RaplaException {
        if ( createNew )
        {
        	createCategory( false);
        }
	}


    private void createCategory(boolean bCreateSubCategory) throws RaplaException {
        confirmEdits();
        Category newCategory;
        NamedNode parentNode;
        TreePath path = treeEdit.getTree().getSelectionPath();
        if (path == null) {
            parentNode = (NamedNode)model.getRoot();
        } else {
        	NamedNode selectedNode = (NamedNode) path.getLastPathComponent();
            if (selectedNode.getParent() == null || bCreateSubCategory)
                parentNode = selectedNode;
            else
                parentNode = (NamedNode)selectedNode.getParent();
        }
        newCategory = createNewNodeAt( parentNode );
        updateModel();
        NamedNode newNode = (NamedNode)((NamedNode)model.getRoot()).findNodeFor( newCategory );
        TreePath selectionPath = new TreePath( newNode.getPath() );
        treeEdit.getTree().setSelectionPath( selectionPath );
        detailPanel.name.selectAll();
        detailPanel.name.requestFocus();
    }


    private String createNewKey(Category[] subCategories) {
        int max = 1;
        for (int i=0;i<subCategories.length;i++) {
            String key = subCategories[i].getKey();
            if (key.length()>1
                && key.charAt(0) =='c'
                && Character.isDigit(key.charAt(1))
                )
                {
                    try {
                        int value = Integer.valueOf(key.substring(1)).intValue();
                        if (value >= max)
                            max = value + 1;
                    } catch (NumberFormatException ex) {
                    }
                }
        }
        return "c" + (max);
    }

    // creates a new Category
    private Category createNewNodeAt(NamedNode parentNode) throws RaplaException {
        Category newCategory = getModification().newCategory();

        Category parent =  (Category) parentNode.getUserObject();
        newCategory.setKey(createNewKey(parent.getCategories()));
        newCategory.getName().setName(getI18n().getLang(), getString("new_category") );
        parent.addCategory(newCategory);
        getLogger().debug(" new category " + newCategory + " added to " + parent);
        return newCategory;
    }

    private void removeCategory() 
    {
        TreePath[] paths = treeEdit.getTree().getSelectionPaths();
        if ( paths == null )
            return;
        NamedNode[] categoryNodes = new NamedNode[paths.length];
        for (int i=0;i<paths.length;i++) {
            categoryNodes[i] = (NamedNode) paths[i].getLastPathComponent();
        }
        removeNodes(categoryNodes);
        updateModel();
    }

    private void moveCategory( int direction ) 
    {
        TreePath[] paths = treeEdit.getTree().getSelectionPaths();
        if ( paths == null || paths.length == 0)
            return;
        NamedNode categoryNode = (NamedNode)paths[0].getLastPathComponent();
        if ( categoryNode == null)
        {
            return;
        }
        Category selectedCategory = (Category)categoryNode.getUserObject();
        Category parent = selectedCategory.getParent();
        if ( parent == null || selectedCategory.equals( rootCategory))
            return;

        Category[] childs = parent.getCategories();
        for ( int i=0;i<childs.length;i++)
        {
            parent.removeCategory( childs[i]);
        }
        if ( direction == -1)
        {
            Category last = null;
            for ( int i=0;i<childs.length;i++)
            {
                Category current = childs[i];
                if ( current.equals( selectedCategory)) {
                    parent.addCategory( current);
                }
                if ( last != null && !last.equals( selectedCategory))
                {
                    parent.addCategory(last);
                }
                last = current;
            }
            if  (last != null && !last.equals( selectedCategory)) {
                parent.addCategory(last);
            }
        }
        else
        {
            boolean insertNow = false;
            for ( int i=0;i<childs.length;i++)
            {
                Category current = childs[i];
                if ( !current.equals( selectedCategory)) {
                    parent.addCategory( current);
                } else {
                    insertNow = true;
                    continue;
                }
                if ( insertNow)
                {
                    insertNow = false;
                    parent.addCategory( selectedCategory);
                }
            }
            if  ( insertNow) {
                parent.addCategory( selectedCategory);
            }
        }
        updateModel();

    }

    public void removeNodes(NamedNode[] nodes) {
        ArrayList<NamedNode> childList = new ArrayList<NamedNode>();
        TreeNode[] path = null;
        NamedNode parentNode = null;
        for (int i=0;i<nodes.length;i++) {
            if (parentNode == null) {
                path= nodes[i].getPath();
                parentNode = (NamedNode)nodes[i].getParent();
            }
            // dont't delete the root-node
            if (parentNode == null)
                continue;
            int index = parentNode.getIndexOfUserObject(nodes[i].getUserObject());
            if (index >= 0) {
                childList.add(nodes[i]);
            }
        }
        if (path != null) {
            int size = childList.size();
            NamedNode[] childs = new NamedNode[size];
            for (int i=0;i<size;i++) {
                childs[i] = childList.get(i);
            }
            for (int i=0;i<size;i++) {
                Category subCategory = (Category)childs[i].getUserObject();
                subCategory.getParent().removeCategory(subCategory);
                getLogger().debug("category removed " + subCategory);
            }
        }
    }

    public void mapToObjects() throws RaplaException {
        validate( this.rootCategory );
        confirmEdits();
    }

    public List<Category> getObjects() {
        return Collections.singletonList(this.rootCategory);
    }

    private void updateModel()  {
        model = getTreeFactory().createModel( rootCategory);
        RaplaTree.exchangeTreeModel( model , treeEdit.getTree() );
    }

    public void confirmEdits() throws RaplaException {
        if ( getSelectedIndex() < 0 )
            return;
        Category category = (Category) treeEdit.getSelectedValue();
        detailPanel.mapTo ( category );
        TreePath path = treeEdit.getTree().getSelectionPath();
        if (path != null)
            ((DefaultTreeModel) model).nodeChanged((TreeNode)path.getLastPathComponent() );
    }


    private void validate(Category category) throws RaplaException {
        checkKey( category.getKey() );
        Category[] categories = category.getCategories();
        for ( int i=0; i< categories.length;i++) {
            validate( categories[i] );
        }
    }

    private void checkKey(String key) throws RaplaException {
        if (key.length() ==0)
            throw new RaplaException(getString("error.no_key"));
        if (!Tools.isKey(key) || key.length()>50)
        {
        	Object[] param = new Object[3];
        	param[0] = key;
        	param[1] = "'-', '_'";
        	param[2] = "'_'";
            throw new RaplaException(getI18n().format("error.invalid_key", param));
    	}
    }
    public void setEditKeys(boolean editKeys) {
        detailPanel.setEditKeys(editKeys);
        this.editKeys = editKeys;
    }


}

class CategoryDetail extends RaplaGUIComponent
    implements
    ChangeListener
{
	
	JPanel mainPanel = new JPanel();
	Category currentCategory;
	
    JPanel panel = new JPanel();
    JLabel nameLabel = new JLabel();
    JLabel keyLabel = new JLabel();
    JLabel colorLabel = new JLabel();

    MultiLanguageField name;
    TextField key;
    TextField colorTextField;
    JPanel colorPanel = new JPanel();
    
	RaplaArrowButton addButton = new RaplaArrowButton('>', 25);
	RaplaArrowButton removeButton = new RaplaArrowButton('<', 25);


    public CategoryDetail(RaplaContext context) 
    {
        super( context);
        name = new MultiLanguageField(context);
        key = new TextField(context);
        colorTextField = new TextField(context);
        colorTextField.setColorPanel( true );
        
        double fill = TableLayout.FILL;
        double pre = TableLayout.PREFERRED;
        panel.setLayout( new TableLayout( new double[][]
            {{5, pre, 5, fill },  // Columns
             {5, pre ,5, pre, 5, pre, 5}} // Rows
                                          ));
        panel.add("1,1,l,f", nameLabel);
        panel.add("3,1,f,f", name.getComponent() );
        panel.add("1,3,l,f", keyLabel);
        panel.add("3,3,f,f", key.getComponent() );
        panel.add("1,5,l,f", colorLabel);
        panel.add("3,5,f,f", colorPanel);
        colorPanel.setLayout( new BorderLayout());
        colorPanel.add( colorTextField.getComponent(), BorderLayout.CENTER );
      

        nameLabel.setText(getString("name") + ":");
        keyLabel.setText(getString("key") + ":");
        colorLabel.setText( getString("color") + ":");
        name.addChangeListener ( this );
        key.addChangeListener ( this );
        colorTextField.addChangeListener( this );


        // Add everything to the MainPanel
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(panel, BorderLayout.NORTH);

    }
    
    class CategoryListCellRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;
		private boolean filterStyle;

		public CategoryListCellRenderer(boolean filterStyle) {
			this.filterStyle = filterStyle;
		}

		public CategoryListCellRenderer() {
			this(false);
		}

		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected,
					cellHasFocus);

			if (filterStyle == true)
				setFont((getFont().deriveFont(Font.PLAIN)));

			if (value != null && value instanceof Category) {
				setText(((Category) value).getName(getLocale()));
			}
			return this;
		}
	}

    public void requestFocus() {
        name.requestFocus();
    }

    public void setEditKeys(boolean editKeys) {
        keyLabel.setVisible( editKeys );
        key.getComponent().setVisible( editKeys );
        colorLabel.setVisible( editKeys );
        colorTextField.getComponent().setVisible( editKeys );
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    public void mapFrom(Category category) {
        name.setValue( category.getName());
        key.setValue( category.getKey());
        String color = category.getAnnotation( CategoryAnnotations.KEY_NAME_COLOR);
        if ( color != null)
        {
        	colorTextField.setValue( color );
        }
        else
        {
        	colorTextField.setValue( null );
        }
		currentCategory = category;
    }

    
    public void mapTo(Category category) throws RaplaException {
        category.getName().setTo( name.getValue());
        category.setKey( key.getValue());
        String colorValue = colorTextField.getValue().toString().trim();
        if ( colorValue.length() > 0) {
            category.setAnnotation(CategoryAnnotations.KEY_NAME_COLOR, colorValue );
        } else {
            category.setAnnotation(CategoryAnnotations.KEY_NAME_COLOR, null );
        }
    }

    public void stateChanged(ChangeEvent e) {
        fireContentChanged();
    }
    
    
    ArrayList<ChangeListener> listenerList = new ArrayList<ChangeListener>();

    public void addChangeListener(ChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.toArray(new ChangeListener[]{});
    }

    protected void fireContentChanged() {
        if (listenerList.size() == 0)
            return;
        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].stateChanged(evt);
        }
    }

 
}



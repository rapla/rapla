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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.RaplaTree.TreeIterator;
import org.rapla.components.util.Tools;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;


public abstract class AbstractSelectField<T> extends AbstractEditField implements MultiEditField, SetGetField<T>, SetGetCollectionField<T>
{
    private RaplaButton selectButton = new RaplaButton(RaplaButton.SMALL);
    JPanel panel;
    JLabel selectText = new JLabel();
    private Collection<T> selectedValues = new ArrayList<T>();
    T defaultValue;

    private boolean useDefault = true;
    private boolean useNull = true;
    boolean multipleValues = false;
    boolean multipleSelectionPossible = false;
    private final TreeFactory treeFactory;
    private final DialogUiFactoryInterface dialogUiFactory;
  
    public RaplaButton getButton() {
        return selectButton;
    }

    public AbstractSelectField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory){
       this( facade, i18n, raplaLocale, logger, treeFactory, raplaImages, dialogUiFactory, null);
    }
    
    public AbstractSelectField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, T defaultValue) {
        super(facade, i18n, raplaLocale, logger);
        this.treeFactory = treeFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.panel = new JPanel()
        {
            @Override
            public void setEnabled(boolean enabled)
            {
                super.setEnabled(enabled);
                selectButton.setEnabled(enabled);
            }
        };
        useDefault = defaultValue != null;
        selectButton.setAction(new SelectionAction());
        selectButton.setHorizontalAlignment(RaplaButton.LEFT);
        selectButton.setText(getString("select"));
        selectButton.setIcon(raplaImages.getIconFromKey("icon.tree"));
        panel.setLayout( new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add( selectButton);
        panel.add( Box.createHorizontalStrut(10));
        panel.add( selectText);
        this.defaultValue = defaultValue;
    }
    
    protected TreeFactory getTreeFactory()
    {
        return treeFactory;
    }
    
    public boolean isUseNull() 
    {
        return useNull;
    }

    public void setUseNull(boolean useNull) 
    {
        this.useNull = useNull;
    }

    public boolean isMultipleSelectionPossible() {
        return multipleSelectionPossible;
    }

    public void setMultipleSelectionPossible(boolean multipleSelectionPossible) {
        this.multipleSelectionPossible = multipleSelectionPossible;
    }
    
    public class SelectionAction extends AbstractAction 
    {
        private static final long serialVersionUID = 1L;

        public void actionPerformed(ActionEvent evt) {
            try {
                showDialog(selectButton);
            } catch (RaplaException ex) {
                dialogUiFactory.showException(ex,new SwingPopupContext(selectButton, null));
            }
        }
    }

    public T getValue() 
    {
    	Collection<T> values = getValues();
    	if ( values.size() == 0)
    	{
    	    return null;
    	}
    	else
    	{
    	    T first = values.iterator().next();
            return first;
    	}
    }
    
    public Collection<T> getValues() 
    {
        return selectedValues;
    }
    
    public void setValue(T object) 
    {
        List<T> list;
        if ( object == null)
        {
            list = Collections.emptyList();
        }
        else
        {
            list = Collections.singletonList(object);
        }
        setValues(list);
    }

    public void setValues(Collection<T> values) 
    {
        selectedValues = new ArrayList<T>();
        if ( values !=null)
        {
            selectedValues.addAll(values);
        }
        String text;
        if (selectedValues.size() > 0) 
        {
        	text="";
        	T selectedCategory = selectedValues.iterator().next();
        	{
        		
        		text +=getNodeName(selectedCategory);
        	}
        	if ( selectedValues.size() > 1)
            {
                text+= ", ...";
            }
        } 
        else 
        {
            text = getString("nothing_selected");
        }
        selectText.setText(text);
        multipleValues = false;
    }

	protected abstract String getNodeName(T selectedCategory);
   
    public class MultiSelectionTreeUI extends BasicTreeUI
    {

        @Override
        protected boolean isToggleSelectionEvent( MouseEvent event )
        {
            return SwingUtilities.isLeftMouseButton( event );
        }
    }

    @SuppressWarnings("serial")
	public void showDialog(JComponent parent) throws RaplaException {
        final DialogInterface dialog;
        final JTree tree;
        if ( multipleSelectionPossible)
        {
	        tree = new JTree()
	        {
		        public void setSelectionPath(TreePath path) {
		            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		            StackTraceElement caller = stackTrace[2];
                    String className = caller.getClassName();
                    String methodName = caller.getMethodName();
                    
                    if ( className.contains("BasicTreeUI") && (methodName.contains("keyTyped") || methodName.contains("page")))
		            {
                        setLeadSelectionPath( path);
		                return;
		            }
		        	setSelectionPath(path);
		        }
		        public void setSelectionInterval(int index0, int index1) {
		            if ( index0 >= 0)
		            {
		                TreePath path = getPathForRow(index0);
		                setLeadSelectionPath( path);
		            }
		        }
	        };
	        TreeSelectionModel model = new DefaultTreeSelectionModel();
            tree.setUI( new MultiSelectionTreeUI() );
	        model.setSelectionMode( TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION );
	        tree.setSelectionModel(model );
        }
        else
        {
        	tree = new JTree();
        	tree.getSelectionModel().setSelectionMode( TreeSelectionModel.SINGLE_TREE_SELECTION );
        }
        tree.setCellRenderer(treeFactory.createRenderer());
        //tree.setVisibleRowCount(15);
        tree.setRootVisible( false );
        tree.setShowsRootHandles(true);
        TreeModel model = createModel();
		tree.setModel(model);
        selectValues(tree,selectedValues);
        JPanel panel = new JPanel();
        panel.setLayout( new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setMinimumSize(new Dimension(300, 200));
        scrollPane.setPreferredSize(new Dimension(400, 260));
        panel.add(scrollPane, BorderLayout.PAGE_START);
        
        if (useDefault)
        {
            JButton defaultButton = new JButton(getString("defaultselection"));
            panel.add( defaultButton,  BorderLayout.CENTER);
            defaultButton.setPreferredSize(new Dimension(100, 20));
            defaultButton.addActionListener( new ActionListener() {
                public void actionPerformed(ActionEvent arg0) 
                {
                    selectValues( tree, Collections.singletonList(defaultValue));
                }
            });
        }
        
        if (useNull)
        {
            JButton emptyButton = new JButton(getString("nothing_selected"));
            panel.add( emptyButton, BorderLayout.PAGE_END);
            emptyButton.setPreferredSize(new Dimension(100, 20));
            emptyButton.addActionListener( new ActionListener() {
                public void actionPerformed(ActionEvent arg0) 
                {
                    List<T> emptyList = Collections.emptyList();
					selectValues(tree, emptyList );
                }
            });
        }

        dialog = dialogUiFactory.create(
                new SwingPopupContext(parent, null)
                                 ,true
                                 ,panel
                                 ,new String[] { getString("apply"),getString("cancel")});

        final Collection<T> newValues = new LinkedHashSet<T>();
        tree.addMouseListener(new MouseAdapter() {
            // End dialog when a leaf is double clicked
            public void mousePressed(MouseEvent e) {
                // we only add the double click when multiselect is not enabled
                // because it will cause a deselect on double clicking an item
                if ( multipleSelectionPossible)
                {
                    return;
                }
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                if (selPath != null && e.getClickCount() == 2) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode)selPath.getLastPathComponent();
                    if (node.isLeaf()) {
                        newValues.addAll(getValues(tree));
                        dialog.getAction(0).execute();
                    }
                }
            }
        });
        dialog.setTitle(getString("select"));
        dialog.start(true);
        tree.requestFocus();
        // we did a double clidk
        if ( !newValues.isEmpty())
        {
            if ( !newValues.equals(selectedValues))
            {
                setValues(newValues);
                fireContentChanged();
            }
        }
        else if (dialog.getSelectedIndex() == 0 ) {
            newValues.addAll(getValues(tree));
            if ( !newValues.equals(selectedValues))
            {
                setValues(newValues);
                fireContentChanged();
            }
        }
    }

    private Collection<T> getValues(JTree tree)
    {
        Collection<T> newValues = new LinkedHashSet<T>();
        TreePath[] paths = tree.getSelectionPaths();
        if ( paths != null)
        {
            for (TreePath path:paths)
            {
                Object valueObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
                T value = getValue(valueObject);
                if ( value != null)
                {
                    newValues.add(value);
                }
            }
        }
        return newValues;
    }

    protected T getValue(Object valueObject) {
		@SuppressWarnings("unchecked")
		T casted = (T) valueObject;
		return casted;
	}

	public abstract TreeModel createModel() throws RaplaException;

    private void selectValues(JTree tree, Collection<T> values) {
        select(tree, values);
        //RecursiveNode.selectUserObjects(tree,path.toArray());
    }
    
    public void select(JTree jTree,Collection<?> selectedObjects) {
        Collection<TreeNode> selectedNodes = new ArrayList<TreeNode>();
        {
            Iterator<TreeNode> it = new TreeIterator((TreeNode)jTree.getModel().getRoot());
            while (it.hasNext()) {
                TreeNode node = it.next();
                if (node != null && selectedObjects.contains( getObject(node) ))
                    selectedNodes.add(node);
            }
        }
        TreePath[] paths = new TreePath[selectedNodes.size()];
        int i=0;
        for ( TreeNode node: selectedNodes)
        {
            paths[i] = getPath(node);
            jTree.expandPath(paths[i]);
            i++;
        }
        TreeSelectionModel selectionModel = jTree.getSelectionModel();
        selectionModel.clearSelection();
        selectionModel.setSelectionPaths(paths);
        jTree.setSelectionPaths(paths);
    }
    
    private static Object getObject(Object treeNode) {
        try {
            if (treeNode == null)
                return null;
            if (treeNode instanceof DefaultMutableTreeNode)
                return ((DefaultMutableTreeNode) treeNode).getUserObject();
            
            return treeNode.getClass().getMethod("getUserObject",Tools.EMPTY_CLASS_ARRAY).invoke(treeNode, Tools.EMPTY_ARRAY);
        } catch (Exception ex) {
            return null;
        }
    }
   
    private TreePath getPath(TreeNode node) {
        TreeNode parent = node.getParent();
        if (parent == null)
            return new TreePath(node);
        else
            return getPath(parent).pathByAddingChild(node);
    }
    
    public JComponent getComponent() {
        return panel;
    }
    
 // implementation for interface MultiEditField
    @Override
 	public boolean hasMultipleValues() {
 		return multipleValues;
 	}

 	// implementation for interface MultiEditField
    @Override
 	public void setFieldForMultipleValues() {
 		multipleValues = true;
 		// sets place holder for different values
 		selectText.setText(TextField.getOutputForMultipleValues());
 	}

}



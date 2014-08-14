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
package org.rapla.gui.toolkit;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.rapla.components.util.Tools;

/** Encapsulates the complex tree class and provides some basic functionality like
 *  life model exchanging while keeping the Tree state or the integration of the popup listener.
 */
final public class RaplaTree extends JScrollPane {
    private static final long serialVersionUID = 1L;
    
    ArrayList<PopupListener> m_popupListeners = new ArrayList<PopupListener>();
    ArrayList<ActionListener> m_doubleclickListeners = new ArrayList<ActionListener>();
    ArrayList<ChangeListener> m_changeListeners = new ArrayList<ChangeListener>();
    JTree       jTree =  new JTree() {
        private static final long serialVersionUID = 1L;
        public String getToolTipText(MouseEvent evt) {
            if (toolTipRenderer == null)
            {
                return super.getToolTipText(evt);
            }
            int row = getRowForLocation(evt.getX(),evt.getY());
            if (row >=0)
            {
                return toolTipRenderer.getToolTipText(this,row);
            }
            return super.getToolTipText(evt);
        }
      
        public Point getToolTipLocation(MouseEvent evt) {
            return new Point(getWidth(), 0);
          }
        
        /**
         * Overwrite the standard method for performance reasons.
         *
         * @see javax.swing.JTree#getExpandedDescendants(javax.swing.tree.TreePath)
         */
//        @Override
//        public Enumeration getExpandedDescendants(final TreePath parent) {
//            return null;
//        }

    };
    Listener listener = new Listener();

    private boolean treeSelectionListenerBlocked = false;
    private boolean bMultiSelect = false;
    TreePath selectedPath = null;
    TreeToolTipRenderer toolTipRenderer;

    public RaplaTree() {
        jTree.setBorder( BorderFactory.createEtchedBorder(Color.white,new Color(178, 178, 178)));
        jTree.setRootVisible(false);
        jTree.setShowsRootHandles(true);
        //jTree.putClientProperty("JTree.lineStyle", "None");
        getViewport().add(jTree, null);

        jTree.addTreeSelectionListener( listener );
        jTree.addMouseListener( listener );
        setMultiSelect(bMultiSelect);
    }

    public void setToolTipRenderer(TreeToolTipRenderer renderer) {
        toolTipRenderer = renderer;
    }

    public void addChangeListener(ChangeListener listener) {
        m_changeListeners.add(listener);
    }
    public void removeChangeListener(ChangeListener listener) {
        m_changeListeners.remove(listener);
    }

    /** An ChangeEvent will be fired to every registered ChangeListener
     *  when the selection has changed.
    */
    protected void fireValueChanged() {
        if (m_changeListeners.size() == 0)
            return;
        ChangeListener[] listeners = getChangeListeners();
        ChangeEvent evt = new ChangeEvent(this);
        for (int i = 0;i<listeners.length;i++) {
            listeners[i].stateChanged(evt);
        }
    }

    public ChangeListener[] getChangeListeners() {
        return m_changeListeners.toArray(new ChangeListener[]{});
    }

    public TreeToolTipRenderer getToolTipRenderer() {
        return toolTipRenderer;
    }

    public void addDoubleclickListeners(ActionListener listener) {
        m_doubleclickListeners.add(listener);
    }

    public void removeDoubleclickListeners(ActionListener listener) {
        m_doubleclickListeners.remove(listener);
    }

    public ActionListener[] getDoubleclickListeners() {
        return m_doubleclickListeners.toArray(new ActionListener[]{});
    }
    
    public void addPopupListener(PopupListener listener) {
        m_popupListeners.add(listener);
    }

    public void removePopupListener(PopupListener listener) {
        m_popupListeners.remove(listener);
    }

    public PopupListener[] getPopupListeners() {
        return m_popupListeners.toArray(new PopupListener[]{});
    }

    /** An PopupEvent will be fired to every registered PopupListener
     *  when the popup is selected
    */
    protected void firePopup(MouseEvent me) {
        Point p = new Point(me.getX(), me.getY());
        if (m_popupListeners.size() == 0)
            return;
        PopupListener[] listeners = getPopupListeners();
        Object selectedObject =  null;
        TreePath path = getTree().getPathForLocation(p.x,p.y);
        if (path != null) {
            Object node = path.getLastPathComponent();
            if (node != null) {
                if (node instanceof DefaultMutableTreeNode)
                    selectedObject = ((DefaultMutableTreeNode)node).getUserObject();
            }
        }
        Point upperLeft = getViewport().getViewPosition();
        Point newPoint = new Point(p.x - upperLeft.x + 10
                                   ,p.y-upperLeft.y);
        PopupEvent evt = new PopupEvent(this, selectedObject, newPoint);
        for (int i = 0;i<listeners.length;i++) {
            listeners[i].showPopup(evt);
        }
    }

    protected void fireEdit(MouseEvent me) {
        Point p = new Point(me.getX(), me.getY());
        if (m_doubleclickListeners.size() == 0)
            return;
        ActionListener[] listeners = getDoubleclickListeners();
        Object selectedObject =  null;
        TreePath path = getTree().getPathForLocation(p.x,p.y);
        if (path != null) {
            Object node = path.getLastPathComponent();
            if (node != null) {
                if (node instanceof DefaultMutableTreeNode)
                {
                    selectedObject = ((DefaultMutableTreeNode)node).getUserObject();
                }
            }
        }
        if (selectedObject != null) {
            ActionEvent evt = new ActionEvent( selectedObject, ActionEvent.ACTION_PERFORMED, "");
            for (int i = 0;i<listeners.length;i++) {
                listeners[i].actionPerformed(evt);
            }
        }
    }

    public JTree getTree() {
        return jTree;
    }

    class Listener  implements MouseListener,TreeSelectionListener {
        public void valueChanged(TreeSelectionEvent event) {
            if ( event.getSource() == jTree && ! treeSelectionListenerBlocked) {
                selectedPath = event.getNewLeadSelectionPath();
                fireValueChanged();
            }
        }
        public void mouseEntered(MouseEvent me) {
        }
        public void mouseExited(MouseEvent me) {
        }
        public void mousePressed(MouseEvent me) {
            if (me.isPopupTrigger())
                firePopup(me);
        }
        public void mouseReleased(MouseEvent me) {
            if (me.isPopupTrigger())
                firePopup(me);
        }
        public void mouseClicked(MouseEvent me) {
        	 TreePath selectionPath = jTree.getSelectionPath();
			if (me.getClickCount() == 2 && selectionPath != null )
        	 {
                final Object lastPathComponent = selectionPath.getLastPathComponent();
                if ( lastPathComponent instanceof TreeNode)
                {
                    if (( (TreeNode) lastPathComponent).isLeaf())
                    {
                        fireEdit(me);
                    }
                }
            	// System.out.println("mouse Clicked > 1");
            	// System.out.println("Button= " + me.getButton() + "Cliks= " + me.getClickCount() + " " + me.getComponent().getClass().getName());
            }
        }
    }

    public void setEnabled(boolean enabled) {
        jTree.setEnabled(enabled);
    }

    private Object getFromNode(TreeNode node) {
        if (node == null) return null;
        return getObject(node);
    }

    private Object getLastSelectedElement() {
        if (selectedPath != null) {
            TreeNode node = (TreeNode)
                selectedPath.getLastPathComponent();
            return getFromNode(node);
        } else {
            return null;
        }
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

    public void exchangeTreeModel(TreeModel model) {
        boolean notifySelection;
        try {
            treeSelectionListenerBlocked  = true;
            notifySelection =  exchangeTreeModel( model, jTree ) ;
        } finally {
            treeSelectionListenerBlocked  = false;
        }
        if ( notifySelection ) {
            this.fireValueChanged();
        }
    }
    
    public void exchangeTreeModel2(TreeModel model) {
        try {
            treeSelectionListenerBlocked  = true;
            jTree.setModel(model);
        } finally {
            treeSelectionListenerBlocked  = false;
        }
    }

    /** Exchanges the tree-model while trying to preserve the selection an expansion state.
     * Returns if the selection has been affected by the excahnge.*/
    public static boolean exchangeTreeModel(TreeModel model,JTree tree) {
        Collection<Object> expanded = new LinkedHashSet<Object>();
        Collection<Object> selected = new LinkedHashSet<Object>();
        int rowCount = tree.getRowCount();
		for (int i=0;i<rowCount;i++) {
            if (tree.isExpanded(i)) {
                Object obj = getObject( tree.getPathForRow(i).getLastPathComponent() );
                if (obj != null )
                    expanded.add( obj );
            }
            if (tree.isRowSelected(i)) {
                Object obj = getObject( tree.getPathForRow(i).getLastPathComponent() );
                if (obj != null )
                    selected.add( obj );
            }
        }
		tree.setModel(model);
        if ( model instanceof DefaultTreeModel ) {
            ((DefaultTreeModel)model).reload();
        }
        if (expanded.size() ==0 && selected.size() == 0)
        {
            TreeNode root = (TreeNode)model.getRoot();
            if (root.getChildCount()<2)
            {
                tree.expandRow(0);
            }

        }
        ArrayList<TreePath> selectedList = new ArrayList<TreePath>();
        for (int i=0;i<rowCount;i++) {
            TreePath treePath = tree.getPathForRow(i);
            if (treePath != null)
            {
	            Object obj = getObject( treePath.getLastPathComponent() );
	            if (obj == null)
	                continue;
	
	            if (expanded.contains( obj )) {
	            	expanded.remove( obj );
	                tree.expandRow(i);
	            }
	            if (selected.contains( obj )) {
	            	selected.remove( obj );
	                selectedList.add(treePath);
	            }
            }

        }
        tree.setSelectionPaths(selectedList.toArray(new TreePath[selectedList.size()]));
        return  selectedList.size() != selected.size();
    }


    public void setMultiSelect(boolean bMultiSelect) {
        this.bMultiSelect = bMultiSelect;
        if ( bMultiSelect) {
            jTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        } else {
            jTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        } // end of else
    }

    public static class TreeIterator implements Iterator<TreeNode> {
        Stack<TreeNode> nodeStack = new Stack<TreeNode>();
        public TreeIterator(TreeNode node) {
            nodeStack.push(node);
        }
        public boolean hasNext() {
            return !nodeStack.isEmpty();
        }
        public TreeNode next() {
            TreeNode node =  nodeStack.pop();
            int count = node.getChildCount();
            for (int i=count-1;i>=0;i--) {
                nodeStack.push(node.getChildAt(i));
            }
            return node;
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private TreePath getPath(TreeNode node) {
        if (node.getParent() == null)
            return new TreePath(node);
        else
            return getPath(node.getParent()).pathByAddingChild(node);
    }

    public void select(Collection<Object> selectedObjects) {
        Collection<TreeNode> selectedNodes = new ArrayList<TreeNode>();
        Collection<Object> selectedToRemove = new LinkedHashSet<Object>( );
        selectedToRemove.addAll( selectedObjects);
        Iterator<TreeNode> it = new TreeIterator((TreeNode)jTree.getModel().getRoot());
        while (it.hasNext()) {
            TreeNode node = it.next();
            Object object = getObject(node);
			if (node != null && selectedToRemove.contains( object ))
            {
                selectedNodes.add(node);
                selectedToRemove.remove( object);
            }
        }
        TreePath[] path = new TreePath[selectedNodes.size()];
        int i=0;
        it = selectedNodes.iterator();
        while (it.hasNext()) {
            path[i] = getPath(it.next());
            jTree.expandPath(path[i]);
            i++;
        }
        jTree.setSelectionPaths(path);
    }
    
    public Object getSelectedElement() {
        Collection<Object> col = getSelectedElements();
        if ( col.size()>0) {
            return col.iterator().next();
        } else {
            return null;
        } // end of else
    }

    public List<Object> getSelectedElements() {
    	return getSelectedElements( false);
    }
    
    public List<Object> getSelectedElements(boolean includeChilds) {
        TreePath[] path = jTree.getSelectionPaths();
        List<Object> list = new LinkedList<Object>();
        if ( path == null)
        {
            return list;
        }
        for (TreePath p:path) {
            TreeNode node = (TreeNode) p.getLastPathComponent();
            Object obj = getFromNode(node);
            if (obj != null)
                list.add(obj);
            if ( includeChilds )
            {
            	addChildNodeObjects(list, node);
            }
        }
        return list;
    }

	protected void addChildNodeObjects(List<Object> list, TreeNode node) {
		int childCount = node.getChildCount();
		for ( int i = 0;i<childCount;i++)
		{
			TreeNode child = node.getChildAt( i);
			Object obj = getFromNode(child);
		    if (obj != null)
		        list.add(obj);
		    addChildNodeObjects(list, child);
		}
	}

    public Object getInfoElement() {
        if ( bMultiSelect) {
            return getLastSelectedElement();
        } else {
            return getSelectedElement();
        } // end of else
    }

    public void unselectAll() {
        jTree.setSelectionInterval(-1,-1);
    }

    public void requestFocus() {
        jTree.requestFocus();
    }

    public void expandAll() {
        int i = 0;
        while (i<jTree.getRowCount()) {
            jTree.expandRow(i);
            i++;
        }
    }
}










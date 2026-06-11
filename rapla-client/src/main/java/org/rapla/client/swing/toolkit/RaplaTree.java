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
package org.rapla.client.swing.toolkit;

import org.rapla.scheduler.BiFunction;
import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.scheduler.Promise;

import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/** Encapsulates the complex tree class and provides some basic functionality like
 *  life model exchanging while keeping the Tree state or the integration of the popup listener.
 */
final public class RaplaTree extends JScrollPane {
    private static final long serialVersionUID = 1L;
    
    ArrayList<PopupListener> m_popupListeners = new ArrayList<>();
    ArrayList<ActionListener> m_doubleclickListeners = new ArrayList<>();
    ArrayList<ChangeListener> m_changeListeners = new ArrayList<>();
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
    /** Last user-clicked path + its pixel offset inside the viewport. Programmatic
     *  refreshes (selection re-apply, model rebuild) restore the clicked row to the
     *  same visual position so the tree never moves away from the mouse. */
    private TreePath anchorPath = null;
    private int anchorOffsetY = 0;
    private boolean anchorRestorePending = false;
    /** Every user object whose node is expanded — survives model rebuilds, including
     *  rebuilds that temporarily prune the node away (search-by-name). User collapse
     *  removes the object; a model swap (no collapse events) leaves it remembered. */
    private final Collection<Object> expandedUserObjects = new LinkedHashSet<>();

    public RaplaTree() {
        jTree.setBorder( BorderFactory.createEtchedBorder(Color.white,new Color(178, 178, 178)));
        jTree.setRootVisible(false);
        jTree.setShowsRootHandles(true);
        // selecting a node must never expand collapsed branches (duplicate-node
        // mirroring selects hidden twins) — expansion inserts rows and visually
        // shifts the tree away from the clicked node
        jTree.setExpandsSelectedPaths(false);
        //jTree.putClientProperty("JTree.lineStyle", "None");
        getViewport().add(jTree, null);

        jTree.addTreeSelectionListener( listener );
        jTree.addMouseListener( listener );
        jTree.addTreeExpansionListener(new TreeExpansionListener() {
            public void treeExpanded(TreeExpansionEvent event) {
                Object obj = getObject(event.getPath().getLastPathComponent());
                if (obj != null)
                    expandedUserObjects.add(obj);
            }
            public void treeCollapsed(TreeExpansionEvent event) {
                Object obj = getObject(event.getPath().getLastPathComponent());
                if (obj != null)
                    expandedUserObjects.remove(obj);
            }
        });
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

    private void captureAnchor(TreePath lead) {
        anchorPath = null;
        if (lead == null)
            return;
        Rectangle bounds = jTree.getPathBounds(lead);
        if (bounds == null)
            return;
        anchorPath = lead;
        anchorOffsetY = bounds.y - getViewport().getViewPosition().y;
    }

    /** One-shot: after the current EDT event, scroll so the last clicked row sits at
     *  the viewport offset it had at click time. No-op without a recent click. */
    private void scheduleAnchorRestore() {
        if (anchorPath == null || anchorRestorePending)
            return;
        anchorRestorePending = true;
        SwingUtilities.invokeLater(() -> {
            anchorRestorePending = false;
            restoreAnchorPosition();
            anchorPath = null;
        });
    }

    private void restoreAnchorPosition() {
        if (anchorPath == null)
            return;
        TreePath path = resolveByUserObjects(anchorPath);
        if (path == null)
            return;
        Rectangle bounds = jTree.getPathBounds(path);
        if (bounds == null)
            return;
        Point pos = getViewport().getViewPosition();
        getViewport().setViewPosition(new Point(pos.x, Math.max(0, bounds.y - anchorOffsetY)));
    }

    /** Finds the equivalent of a possibly-stale path in the current model by matching
     *  the user objects along the path (the model may have been rebuilt since). */
    private TreePath resolveByUserObjects(TreePath oldPath) {
        TreeNode root = (TreeNode) jTree.getModel().getRoot();
        Object[] components = oldPath.getPath();
        if (components.length > 0 && components[0] == root)
            return oldPath;
        TreeNode node = root;
        TreePath path = new TreePath(root);
        for (int i = 1; i < components.length; i++) {
            Object wanted = getObject(components[i]);
            TreeNode matched = null;
            for (int c = 0; c < node.getChildCount(); c++) {
                TreeNode child = node.getChildAt(c);
                Object obj = getObject(child);
                if (obj != null && obj.equals(wanted)) {
                    matched = child;
                    break;
                }
            }
            if (matched == null)
                return null;
            node = matched;
            path = path.pathByAddingChild(matched);
        }
        return path;
    }

    /** A user gesture on one node stands for its user object: selecting one occurrence
     *  selects every node carrying the same object, deselecting one deselects all. */
    private void mirrorSelectionAcrossDuplicates(TreeSelectionEvent event) {
        Collection<Object> deselected = new LinkedHashSet<>();
        Collection<Object> selected = new LinkedHashSet<>();
        for (TreePath changed : event.getPaths()) {
            Object obj = getObject(changed.getLastPathComponent());
            if (obj == null)
                continue;
            if (event.isAddedPath(changed)) {
                selected.add(obj);
            } else {
                deselected.add(obj);
            }
        }
        // a click that moves the selection both removes and adds the same object — keep it
        deselected.removeAll(selected);
        Collection<Object> desired = new LinkedHashSet<>(selected);
        TreePath[] currentPaths = jTree.getSelectionPaths();
        if (currentPaths != null) {
            for (TreePath p : currentPaths) {
                Object obj = getObject(p.getLastPathComponent());
                if (obj != null && !deselected.contains(obj))
                    desired.add(obj);
            }
        }
        List<TreePath> target = new ArrayList<>();
        Iterator<TreeNode> it = new TreeIterator((TreeNode) jTree.getModel().getRoot());
        while (it.hasNext()) {
            TreeNode node = it.next();
            Object obj = getObject(node);
            if (obj != null && desired.contains(obj))
                target.add(getPath(node));
        }
        Collection<TreePath> current = currentPaths == null ? Collections.emptySet() : new LinkedHashSet<>(Arrays.asList(currentPaths));
        if (!current.equals(new LinkedHashSet<>(target))) {
            // expandsSelectedPaths makes setSelectionPaths expand the twin's collapsed
            // parent — suppress the scroll that expansion would trigger
            boolean scrollsOnExpand = jTree.getScrollsOnExpand();
            jTree.setScrollsOnExpand(false);
            try {
                treeSelectionListenerBlocked = true;
                jTree.setSelectionPaths(target.toArray(new TreePath[0]));
            } finally {
                treeSelectionListenerBlocked = false;
                jTree.setScrollsOnExpand(scrollsOnExpand);
            }
        }
    }

    class Listener  implements MouseListener,TreeSelectionListener {
        public void valueChanged(TreeSelectionEvent event) {
            if ( event.getSource() == jTree && ! treeSelectionListenerBlocked) {
                selectedPath = event.getNewLeadSelectionPath();
                captureAnchor(selectedPath);
                if ( bMultiSelect ) {
                    mirrorSelectionAcrossDuplicates(event);
                }
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
        Point viewPosition = getViewport().getViewPosition();
        try {
            treeSelectionListenerBlocked  = true;
            // pass the remembered expansion so branches a search prune removed
            // come back expanded when the model contains them again
            notifySelection =  exchangeTreeModel( model, jTree, expandedUserObjects ) ;
        } finally {
            treeSelectionListenerBlocked  = false;
        }
        // keep the tree where the user left it: anchor the last clicked row back to
        // its click-time offset, or just restore the raw scroll position
        if (anchorPath != null) {
            scheduleAnchorRestore();
        } else {
            SwingUtilities.invokeLater(() -> getViewport().setViewPosition(viewPosition));
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
        return exchangeTreeModel(model, tree, Collections.emptySet());
    }

    private static boolean exchangeTreeModel(TreeModel model,JTree tree,Collection<Object> rememberedExpanded) {
        Collection<Object> expanded = new LinkedHashSet<>(rememberedExpanded);
        Collection<Object> selected = new LinkedHashSet<>();
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
        ArrayList<TreePath> selectedList = new ArrayList<>();
        // Restore expansion/selection on EVERY node carrying the object — the same
        // user object can occur in multiple nodes (one per categorization value).
        // Restoring must not move the viewport, so scroll-on-expand is suppressed.
        Collection<Object> restoredSelection = new LinkedHashSet<>();
        boolean scrollsOnExpand = tree.getScrollsOnExpand();
        tree.setScrollsOnExpand(false);
        try {
            // dynamic bound: expanding row i adds rows, making nested branches
            // reachable — the old fixed rowCount capped restore at the pre-swap
            // visible row count and silently dropped deeper expansion/selection
            for (int i=0;i<tree.getRowCount();i++) {
                TreePath treePath = tree.getPathForRow(i);
                if (treePath != null)
                {
                    Object obj = getObject( treePath.getLastPathComponent() );
                    if (obj == null)
                        continue;

                    if (expanded.contains( obj )) {
                        tree.expandRow(i);
                    }
                    if (selected.contains( obj )) {
                        restoredSelection.add( obj );
                        selectedList.add(treePath);
                    }
                }

            }
            tree.setSelectionPaths(selectedList.toArray(new TreePath[selectedList.size()]));
        } finally {
            tree.setScrollsOnExpand(scrollsOnExpand);
        }
        return  restoredSelection.size() != selected.size() - restoredSelection.size();
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
        Stack<TreeNode> nodeStack = new Stack<>();
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
        select(selectedObjects, true);
    }

    /** @param expandSelected expand parents so the selection becomes visible — pass
     *         false when re-applying a selection after a user click, where expanding
     *         collapsed branches would shift the tree away from the clicked node */
    public void select(Collection<Object> selectedObjects, boolean expandSelected) {
        Collection<TreeNode> selectedNodes = new ArrayList<>();
        // The same user object can occur in multiple nodes (one per categorization
        // value) — select every occurrence, not just the first in DFS order.
        Collection<Object> toSelect = new LinkedHashSet<>(selectedObjects);
        Iterator<TreeNode> it = new TreeIterator((TreeNode)jTree.getModel().getRoot());
        while (it.hasNext()) {
            TreeNode node = it.next();
            Object object = getObject(node);
			if (object != null && toSelect.contains( object ))
            {
                selectedNodes.add(node);
            }
        }
        TreePath[] path = new TreePath[selectedNodes.size()];
        // programmatic selection must not move the viewport — suppress the
        // scroll-on-expand behaviour BasicTreeUI applies to expansion events
        boolean scrollsOnExpand = jTree.getScrollsOnExpand();
        jTree.setScrollsOnExpand(false);
        try {
            int i=0;
            it = selectedNodes.iterator();
            while (it.hasNext()) {
                path[i] = getPath(it.next());
                if (expandSelected) {
                    jTree.expandPath(path[i]);
                }
                i++;
            }
            jTree.setSelectionPaths(path);
        } finally {
            jTree.setScrollsOnExpand(scrollsOnExpand);
        }
        scheduleAnchorRestore();
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
        List<Object> list = new LinkedList<>();
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
        // The same user object can be selected in multiple nodes (one per
        // categorization value) — report it once.
        return new ArrayList<>(new LinkedHashSet<>(list));
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

    public void addDragAndDrop(BiFunction<Object,Object,Promise<Void>> moveFunction)
    {
        JTree tree = getTree();
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON);
        tree.setDropTarget(new DropTarget(tree, TransferHandler.MOVE, new DropTargetAdapter()
        {
            private final Rectangle _raCueLine = new Rectangle();
            private final Color _colorCueLine = Color.blue;
            private TreePath lastPath = null;

            @Override
            public void dragOver(DropTargetDragEvent dtde)
            {
                TreePath selectionPath = tree.getSelectionPath();
                TreePath sourcePath = selectionPath.getParentPath();
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                Graphics2D g2 = (Graphics2D) tree.getGraphics();
                final Point dropLocation = dtde.getLocation();
                TreePath path = tree.getClosestPathForLocation(dropLocation.x, dropLocation.y);
                if(isDropAllowed(sourcePath, path, selectedNode))
                {
                    if (lastPath == null || !lastPath.equals(path))
                    {
                        Rectangle raPath;
                        Color color;
                        if ( lastPath != null )
                        {
                            raPath  = tree.getPathBounds(lastPath);
                            if (raPath != null) {
                                color = Color.white;
                                drawLine(g2, raPath, color);
                            }
                        }
                        raPath = tree.getPathBounds(path);
                        color = _colorCueLine;
                        drawLine(g2, raPath, color);
                        lastPath = path;
                    }
                }
                else
                {
                    if(lastPath != null)
                    {
                        Rectangle raPath = tree.getPathBounds(path);
                        if (raPath != null) {
                            drawLine(g2, raPath, Color.white);
                        }
                    }
                    lastPath = null;
                }
            }

            private void drawLine(Graphics2D g2, Rectangle raPath, Color color)
            {
                _raCueLine.setRect(0, raPath.y, tree.getWidth(), 2);
                g2.setColor(color);
                g2.fill(_raCueLine);
            }

            @Override
            public void dragEnter(DropTargetDragEvent dtde)
            {
                TreePath selectionPath = tree.getSelectionPath();
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                if (!(selectedNode.getUserObject() instanceof Category))
                {
                    dtde.rejectDrag();
                    return;
                }
                dtde.acceptDrag(DnDConstants.ACTION_MOVE);
            }

            @Override
            public void drop(DropTargetDropEvent dtde)
            {
                TreePath selectionPath = tree.getSelectionPath();
                TreePath sourcePath = selectionPath.getParentPath();
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                Point dropLocation = dtde.getLocation();
                TreePath targetPath = tree.getClosestPathForLocation(dropLocation.x, dropLocation.y);
                if (isDropAllowed(sourcePath, targetPath, selectedNode))
                {
                    DefaultMutableTreeNode targetParentNode = (DefaultMutableTreeNode) targetPath.getLastPathComponent();
                    try {
                        final Promise<Void> voidPromise;
                        voidPromise = moveFunction.apply(selectedNode.getUserObject(), targetParentNode.getUserObject());
                        voidPromise.execOn(SwingUtilities::invokeLater).thenRun(() ->{
                            dtde.dropComplete(true);
                        });
                    } catch (Throwable e) {
                        throw new IllegalStateException(e);
                    }

                }
                else
                {
                    dtde.rejectDrop();
                    dtde.dropComplete(false);
                }
            }


            private boolean isDropAllowed(TreePath sourcePath, TreePath targetPath, DefaultMutableTreeNode selectedNode)
            {
                if (selectedNode.getUserObject() instanceof Category
                        && ((DefaultMutableTreeNode) targetPath.getLastPathComponent()).getUserObject() instanceof Category)
                {
                    Category targetCategory = (Category) ((DefaultMutableTreeNode) targetPath.getLastPathComponent()).getUserObject();
                    return !targetCategory.getId().equals(Category.SUPER_CATEGORY_REF.getId());
                }
                return false;
            }

        }));
    }

}










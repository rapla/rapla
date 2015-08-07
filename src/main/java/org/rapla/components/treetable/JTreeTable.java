/*
 * Sun Microsystems grants you ("Licensee") a non-exclusive, royalty
 * free, license to use, modify and redistribute this software in
 * source and binary code form, provided that i) this copyright notice
 * and license appear on all copies of the software; and ii) Licensee
 * does not utilize the software in a manner which is disparaging to
 * Sun Microsystems.
 *
 * The software media is distributed on an "As Is" basis, without
 * warranty. Neither the authors, the software developers nor Sun
 * Microsystems make any representation, or warranty, either express
 * or implied, with respect to the software programs, their quality,
 * accuracy, or fitness for a specific purpose. Therefore, neither the
 * authors, the software developers nor Sun Microsystems shall have
 * any liability to you or any other person or entity with respect to
 * any liability, loss, or damage caused or alleged to have been
 * caused directly or indirectly by programs contained on the
 * media. This includes, but is not limited to, interruption of
 * service, loss of data, loss of classroom time, loss of consulting
 * or anticipatory *profits, or consequential damages from the use of
 * these programs.
*/

package org.rapla.components.treetable;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.EventObject;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * Original version Philip Milne and Scott Violet
 * Modified by Christopher Kohlhaas to support editing and keyboard handling.
 */

public class JTreeTable extends JTable {
    private static final long serialVersionUID = 1L;

    private RendererTree tree = new RendererTree();
    private TreeTableEditor treeCellEditor;
    private TableToolTipRenderer toolTipRenderer = null;
    private int focusedRow = -1;
    private String cachedSearchKey = "";

    public JTreeTable(TreeTableModel model) {
        super();

        setTreeTableModel( model );

        // Force the JTable and JTree to share their row selection models.
        ListToTreeSelectionModelWrapper selectionWrapper = new
            ListToTreeSelectionModelWrapper();
        setSelectionModel(selectionWrapper.getListSelectionModel());
        setShowGrid( false);
        // No intercell spacing
        setIntercellSpacing(new Dimension(1, 0));
        setShowVerticalLines(true);

        tree.setEditable(false);
        tree.setSelectionModel(selectionWrapper);
        tree.setShowsRootHandles(true);
        tree.setRootVisible(false);
        setDefaultRenderer( TreeTableModel.class, tree );
        setTreeCellEditor(null);

        // And update the height of the trees row to match that of
        // the table.
        if (tree.getRowHeight() < 1) {
            // Metal looks better like this.
            setRowHeight(22);
        }
    }

    public void setToolTipRenderer(TableToolTipRenderer renderer) {
        toolTipRenderer = renderer;
    }

    public TableToolTipRenderer getToolTipRenderer() {
        return toolTipRenderer;
    }

    public String getToolTipText(MouseEvent evt) {
        if (toolTipRenderer == null)
            return super.getToolTipText(evt);
        Point p = new Point(evt.getX(),evt.getY());
        int column = columnAtPoint(p);
        int row = rowAtPoint(p);
        if (row >=0 && column>=0)
            return toolTipRenderer.getToolTipText(this,row,column);
        else
            return super.getToolTipText(evt);
    }

    /**
     * Overridden to message super and forward the method to the tree.
     * Since the tree is not actually in the component hierarchy it will
     * never receive this unless we forward it in this manner.
     */
    public void updateUI() {
        super.updateUI();
        if(tree != null) {
            tree.updateUI();
        }
        // Use the tree's default foreground and background colors in the
        // table.
        LookAndFeel.installColorsAndFont(this, "Tree.background",
                                         "Tree.foreground", "Tree.font");
    }

    /** Set a custom TreeCellEditor. The default one is a TextField.*/
    public void setTreeCellEditor(TreeTableEditor editor) {
        treeCellEditor = editor;
        setDefaultEditor( TreeTableModel.class, new DelegationgTreeCellEditor(treeCellEditor) );
    }


    /** Returns the tree that is being shared between the model.
        If you set a different TreeCellRenderer for this tree it should
        inherit from DefaultTreeCellRenderer. Otherwise the selection-color
        and focus color will not be set correctly.
    */
    public JTree getTree() {
        return tree;
    }

    /**
     * search for given search term in child nodes of selected nodes
     * @param search    what to search for
     * @param parentNode where to search fo
     * @return first childnode where its tostring representation in tree starts with search term, null if no one found
     */
    private TreeNode getNextTreeNodeMatching(String search, TreeNode parentNode) {
        TreeNode result = null;

        Enumeration<?> children = parentNode.children();
        while (children.hasMoreElements()) {
            TreeNode treeNode = (TreeNode) children.nextElement();
            String compareS = treeNode.toString().toLowerCase();
            if (compareS.startsWith(search)) {
                result = treeNode;
                break;
            }
        }
        return result;
    }

    /**
     * create treepath from treenode
     * @param treeNode  Treenode
     * @return treepath object
     */
    public static TreePath getPath(TreeNode treeNode) {
        List<Object> nodes = new ArrayList<Object>();
        if (treeNode != null) {

            nodes.add(treeNode);
            treeNode = treeNode.getParent();
            while (treeNode != null) {
                nodes.add(0, treeNode);
                treeNode = treeNode.getParent();
            }
        }

        return nodes.isEmpty() ? null : new TreePath(nodes.toArray());
    }


    /** overridden to support keyboard expand/collapse for the tree.*/
    protected boolean processKeyBinding(KeyStroke ks,
                                        KeyEvent e,
                                        int condition,
                                        boolean pressed)
    {
        if (condition == JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            {
                if (tree != null && !isEditing() && getSelectedColumn() == getTreeColumnNumber()) {

                    if (e.getID() == KeyEvent.KEY_PRESSED)
                    {
                        if ( (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyChar() =='+')) {
                            int row = getSelectedRow();
                            if (row >= 0) {
                                if (tree.isExpanded(row)) {
                                    tree.collapseRow(row);
                                } else {
                                    tree.expandPath(tree.getPathForRow(row));
                                }
                            }
                            return true;
                        }

                        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                            int row = getSelectedRow();
                            if (row >= 0) {
                                TreePath pathForRow = tree.getPathForRow(row);
                                // if selected node is expanded than collapse it
                                // else selected parents node
                                if (tree.isExpanded(pathForRow)) {
                                    // only if root is visible we should collapse first level
                                    final boolean canCollapse = pathForRow.getPathCount() > (tree.isRootVisible() ? 0 : 1);
                                    if (canCollapse)
                                        tree.collapsePath(pathForRow);
                                } else {
                                    // only if root is visible we should collapse first level or select parent node
                                    final boolean canCollapse = pathForRow.getPathCount() > (tree.isRootVisible() ? 1 : 2);

                                    if (canCollapse) {
                                        pathForRow  = pathForRow.getParentPath();
                                        final int parentRow = tree.getRowForPath(pathForRow );
                                        tree.setSelectionInterval(parentRow, parentRow);
                                    }
                                }

                                if (pathForRow != null) {
                                    Rectangle rect = getCellRect(tree.getRowForPath(pathForRow), 0, false);
                                    scrollRectToVisible(rect );
                                }



                            }
                            return true;
                        }
                        if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                            int row = getSelectedRow();
                            if (row >= 0) {
                                final TreePath path = tree.getPathForRow(row);
                                if (tree.isCollapsed(path)) {
                                    tree.expandPath(path);
                                }
                            }
                            return true;
                        }

                        // live search in current parent node
                        if ((Character.isLetterOrDigit(e.getKeyChar()))) {
                            char keyChar = e.getKeyChar();


                            // we are searching in the current parent node
                            // first we assume that we have selected a parent node
                            // so we should have children
                            TreePath selectedPath = tree.getSelectionModel().getLeadSelectionPath();
                            TreeNode selectedNode = (TreeNode) selectedPath.getLastPathComponent();


                            // if we don't have children we might have selected a leaf so choose its parent
                            if (selectedNode.getChildCount() == 0) {
                                // set new selectedNode
                                selectedPath = selectedPath.getParentPath();
                                selectedNode = (TreeNode) selectedPath.getLastPathComponent();
                            }

                            // search term
                            String search = ("" + keyChar).toLowerCase();

                            // try to find node with matching searchterm plus the search before
                            TreeNode nextTreeNodeMatching = getNextTreeNodeMatching(cachedSearchKey + search, selectedNode);

                            // if we did not find anything, try to find search term only: restart!
                            if (nextTreeNodeMatching == null) {
                                nextTreeNodeMatching = getNextTreeNodeMatching(search, selectedNode);
                                cachedSearchKey = "";
                            }
                            // if we found a node, select it, make it visible and return true
                            if (nextTreeNodeMatching != null) {
                                TreePath foundPath = getPath(nextTreeNodeMatching);

                                // select our found path
                                this.tree.getSelectionModel().setSelectionPath(foundPath);

                                //make it visible
                                this.tree.expandPath(foundPath);
                                this.tree.makeVisible(foundPath);
                                // Scroll to the found row
                                int row = tree.getRowForPath( foundPath);
                                int col = 0;
                                Rectangle rect = getCellRect(row, col, false);
                                scrollRectToVisible(rect );

                                // store found treepath
                                cachedSearchKey = cachedSearchKey + search;

                                return true;
                            }
                        }
                        cachedSearchKey = "";

                        /* Uncomment this if you don't want to start tree-cell-editing
                           on a non navigation key stroke.

                        if (e.getKeyCode() != e.VK_TAB && e.getKeyCode() != e.VK_F2
                            && e.getKeyCode() != e.VK_DOWN && e.getKeyCode() != e.VK_UP
                            && e.getKeyCode() != e.VK_LEFT && e.getKeyCode() != e.VK_RIGHT
                            && e.getKeyCode() != e.VK_PAGE_UP && e.getKeyCode() != e.VK_PAGE_DOWN
                            )
                            return true;
    */
                    }
            }
        }
        // reset cachedkey to null if we did not find anything

        return super.processKeyBinding(ks,e,condition,pressed);
    }

    public void setTreeTableModel(TreeTableModel model) {
        tree.setModel(model);
        super.setModel(new TreeTableModelAdapter(model));
    }

    /**
     * Workaround for BasicTableUI anomaly. Make sure the UI never tries to
     * resize the editor. The UI currently uses different techniques to
     * paint the renderers and editors; overriding setBounds() below
     * is not the right thing to do for an editor. Returning -1 for the
     * editing row in this case, ensures the editor is never painted.
     */
    public int getEditingRow() {
        int column = getEditingColumn();
        if( getColumnClass(column) == TreeTableModel.class )
            return -1;
        return editingRow;
    }

   /**
     * Returns the actual row that is editing as <code>getEditingRow</code>
     * will always return -1.
     */
    private int realEditingRow() {
        return editingRow;
    }

    /** Overridden to pass the new rowHeight to the tree.  */
    public void setRowHeight(int rowHeight) {
        super.setRowHeight(rowHeight);
        if (tree != null && tree.getRowHeight() != rowHeight)
           tree.setRowHeight( rowHeight );
    }

    private int getTreeColumnNumber() {
        for (int counter = getColumnCount() - 1; counter >= 0;counter--)
            if (getColumnClass(counter) == TreeTableModel.class)
                return counter;
        return -1;
    }

    /** <code>isCellEditable</code> returns true for the Tree-Column, even if it is not editable.
        <code>isCellRealEditable</code> returns true only if the underlying TreeTableModel-Cell is
        editable.
    */
    private boolean isCellRealEditable(int row,int column) {
        TreePath treePath = tree.getPathForRow(row);
        if (treePath == null)
            return false;
        return (((TreeTableModel)tree.getModel()).isCellEditable(treePath.getLastPathComponent()
                                                                 ,column));

    }

    class RendererTree extends JTree implements TableCellRenderer {
        private static final long serialVersionUID = 1L;

        protected int rowToPaint;
        Color borderColor = Color.gray;

        /** Border to draw around the tree, if this is non-null, it will
         * be painted. */
        protected Border highlightBorder;

        public RendererTree() {
            super();
        }

        public void setRowHeight(int rowHeight) {
            if (rowHeight > 0) {
                super.setRowHeight(rowHeight);
                if (
                    JTreeTable.this.getRowHeight() != rowHeight) {
                    JTreeTable.this.setRowHeight(getRowHeight());
                }
            }
        }

        // Move and resize the tree to the table position
        public void setBounds( int x, int y, int w, int h ) {
            super.setBounds( x, 0, w, JTreeTable.this.getHeight() );
        }

        public void paintEditorBackground(Graphics g,int row) {
            tree.rowToPaint = row;
            g.translate( 0, -row * getRowHeight());
            Rectangle rect = g.getClipBounds();
            if (rect.width >0 && rect.height >0)
                super.paintComponent(g);
            g.translate( 0, row * getRowHeight());
        }

        // start painting at the rowToPaint
        public void paint( Graphics g ) {
            int row = rowToPaint;

            g.translate( 0, -rowToPaint * getRowHeight() );
            super.paint(g);

            int x = 0;
            TreePath path = getPathForRow(row);
            Object value = path.getLastPathComponent();
            boolean isSelected = tree.isRowSelected(row);
            x = tree.getRowBounds(row).x;
            if (treeCellEditor != null) {
                x +=  treeCellEditor.getGap(tree,value,isSelected,row);
            } else {
                TreeCellRenderer tcr = getCellRenderer();
                if (tcr instanceof DefaultTreeCellRenderer) {
                    DefaultTreeCellRenderer dtcr = ((DefaultTreeCellRenderer)tcr);
                    // super.paint must have been called before
                    x += dtcr.getIconTextGap() + dtcr.getIcon().getIconWidth();
                }
            }

            // Draw the Table border if we have focus.
            if (highlightBorder != null) {
                highlightBorder.paintBorder(this, g, x, rowToPaint *
                                            getRowHeight(), getWidth() -x,
                                            getRowHeight() );
            }       // Paint the selection rectangle
        }

        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row, int column) {
            Color background;
            Color foreground;
            if (hasFocus)
                focusedRow = row;

            if(isSelected) {
                background = table.getSelectionBackground();
                foreground = table.getSelectionForeground();
            }
            else {
                background = table.getBackground();
                foreground = table.getForeground();
            }
            highlightBorder = null;
            if (realEditingRow() == row && getEditingColumn() == column) {
                background = UIManager.getColor("Table.focusCellBackground");
                foreground = UIManager.getColor("Table.focusCellForeground");
            } else if (hasFocus) {
                highlightBorder = UIManager.getBorder
                    ("Table.focusCellHighlightBorder");
                if (isCellRealEditable(row,convertColumnIndexToModel(column))) {
                    background = UIManager.getColor
                                 ("Table.focusCellBackground");
                    foreground = UIManager.getColor
                                 ("Table.focusCellForeground");
                }
            }

            this.rowToPaint = row;
            setBackground(background);

            TreeCellRenderer tcr = getCellRenderer();
            if (tcr instanceof DefaultTreeCellRenderer) {
                DefaultTreeCellRenderer dtcr = ((DefaultTreeCellRenderer)tcr);
                if (isSelected) {
                    dtcr.setTextSelectionColor(foreground);
                    dtcr.setBackgroundSelectionColor(background);
                }
                else {
                    dtcr.setTextNonSelectionColor(foreground);
                    dtcr.setBackgroundNonSelectionColor(background);
                }
            }
            return this;
        }
    }

    class DelegationgTreeCellEditor  implements TableCellEditor
    {
        TreeTableEditor delegate;
        JComponent lastComp = null;
        int textOffset = 0;

        MouseListener mouseListener = new MouseAdapter() {
                public void mouseClicked(MouseEvent evt)
                {
                    if (lastComp == null)
                        return;
                    if (delegate == null)
                        return;
                    if (evt.getY() < 0 || evt.getY()>lastComp.getHeight())
                        delegate.stopCellEditing();
                    // User clicked left from the text
                    if (textOffset > 0 && evt.getX()< textOffset )
                        delegate.stopCellEditing();
                }
            };

        public DelegationgTreeCellEditor(TreeTableEditor delegate) {
            this.delegate = delegate;
        }

        public void addCellEditorListener(CellEditorListener listener) {
            delegate.addCellEditorListener(listener);
        }

        public void removeCellEditorListener(CellEditorListener listener) {
            delegate.removeCellEditorListener(listener);
        }

        public void cancelCellEditing() {
            delegate.cancelCellEditing();
        }

        public Object getCellEditorValue() {
            return delegate.getCellEditorValue();
        }

        public boolean stopCellEditing() {
            return delegate.stopCellEditing();

        }

        public boolean shouldSelectCell(EventObject anEvent) {
            return true;
        }

        private int getTextOffset(Object value,boolean isSelected,int row) {
            int gap = delegate.getGap(tree,value,isSelected,row);
            TreePath path = tree.getPathForRow(row);
            return tree.getUI().getPathBounds(tree,path).x + gap;
        }

        public boolean inHitRegion(int x,int y) {
            int row = tree.getRowForLocation(x,y);
            TreePath path = tree.getPathForRow(row);
            if (path == null)
                return false;
            int gap =  (delegate != null) ?
                delegate.getGap(tree,null,false,row)
                :16;

            return (x - gap >= tree.getUI().getPathBounds(tree,path).x  || x<0);
        }

        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected,
                                                     int row,
                                                     int column)
        {
            JTreeTable.this.tree.rowToPaint = row;
            JComponent comp =  delegate.getEditorComponent(tree,value,isSelected,row);
            if (lastComp != comp) {
                if (comp != null)
                {
                    comp.removeMouseListener(mouseListener);
                    comp.addMouseListener(mouseListener);
                }
            }
            lastComp = comp;

            textOffset = getTextOffset(value,isSelected,row);
            Border outerBorder = new TreeBorder(0, textOffset , 0, 0,row);
            Border editBorder = UIManager.getBorder("Tree.editorBorder");
            Border border = new CompoundBorder(outerBorder
                                               ,editBorder
                
                    );
            if ( comp != null)
            {
                comp.setBorder(border);
            }
            return comp;
        }

        public boolean isCellEditable( EventObject evt ) {
            int col = getTreeColumnNumber();
            if( evt instanceof MouseEvent ) {
                MouseEvent me = (MouseEvent)evt;
                if (col >= 0) {
                    int xPosRelativeToCell = me.getX() - getCellRect(0, col, true).x;
                    if (me.getClickCount() > 1
                        && inHitRegion(xPosRelativeToCell,me.getY())
                        && isCellRealEditable(tree.getRowForLocation(me.getX(),me.getY())
                                              ,convertColumnIndexToModel(col)))
                        return true;
                    MouseEvent newME = new MouseEvent(tree, me.getID(),
                                                      me.getWhen(), me.getModifiers(),
                                                      xPosRelativeToCell,
                                                      me.getY(), me.getClickCount(),
                                                      me.isPopupTrigger());
                    if (! inHitRegion(xPosRelativeToCell,me.getY()) || me.getClickCount() > 1)
                        tree.dispatchEvent(newME);
                }
                return false;
            }

            if (delegate != null && isCellRealEditable(focusedRow,convertColumnIndexToModel(col)))
                return delegate.isCellEditable(evt);
            else
                return false;
        }
    }


    class TreeBorder implements Border {
        int row;
        Insets insets;

        public TreeBorder(int top,int left,int bottom,int right,int row) {
            this.row = row;
            insets = new Insets(top,left,bottom,right);
        }

        public Insets getBorderInsets(Component c) {
            return insets;
        }

        public void paintBorder(Component c,Graphics g,int x,int y,int width,int height) {
            Shape originalClip = g.getClip();
            g.clipRect(0,0,insets.left -1 ,tree.getHeight());
            tree.paintEditorBackground(g,row);
            g.setClip(originalClip);
        }

        public boolean isBorderOpaque() {
            return false;
        }

    }



    /**
     * ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel
     * to listen for changes in the ListSelectionModel it maintains. Once
     * a change in the ListSelectionModel happens, the paths are updated
     * in the DefaultTreeSelectionModel.
     */
    class ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel implements ListSelectionListener{
        private static final long serialVersionUID = 1L;

        /** Set to true when we are updating the ListSelectionModel. */
        protected boolean         updatingListSelectionModel;

        public ListToTreeSelectionModelWrapper() {
            super();
            getListSelectionModel().addListSelectionListener
                                    (createListSelectionListener());
        }

        /**
         * Returns the list selection model. ListToTreeSelectionModelWrapper
         * listens for changes to this model and updates the selected paths
         * accordingly.
         */
        ListSelectionModel getListSelectionModel() {
            return listSelectionModel;
        }

        /**
         * This is overridden to set <code>updatingListSelectionModel</code>
         * and message super. This is the only place DefaultTreeSelectionModel
         * alters the ListSelectionModel.
         */
        public void resetRowSelection() {
            if(!updatingListSelectionModel) {
                updatingListSelectionModel = true;
                try {
                    super.resetRowSelection();
                }
                finally {
                    updatingListSelectionModel = false;
                }
            }
            // Notice how we don't message super if
            // updatingListSelectionModel is true. If
            // updatingListSelectionModel is true, it implies the
            // ListSelectionModel has already been updated and the
            // paths are the only thing that needs to be updated.
        }

        /**
         * Creates and returns an instance of ListSelectionHandler.
         */
        protected ListSelectionListener createListSelectionListener() {
            return this;
        }

        /**
         * If <code>updatingListSelectionModel</code> is false, this will
         * reset the selected paths from the selected rows in the list
         * selection model.
         */
        protected void updateSelectedPathsFromSelectedRows() {
            if(!updatingListSelectionModel) {
                updatingListSelectionModel = true;
                try {
                    // This is way expensive, ListSelectionModel needs an
                    // enumerator for iterating.
                    int        min = listSelectionModel.getMinSelectionIndex();
                    int        max = listSelectionModel.getMaxSelectionIndex();

                    clearSelection();
                    if(min != -1 && max != -1) {
                        for(int counter = min; counter <= max; counter++) {
                            if(listSelectionModel.isSelectedIndex(counter)) {
                                TreePath     selPath = tree.getPathForRow
                                                            (counter);

                                if(selPath != null) {
                                    addSelectionPath(selPath);
                                }
                            }
                        }
                    }
                } finally {
                    updatingListSelectionModel = false;
                }
            }
        }
        /** Implemention of ListSelectionListener Interface:
         * Class responsible for calling updateSelectedPathsFromSelectedRows
         * when the selection of the list changse.
         */
        public void valueChanged(ListSelectionEvent e) {
            updateSelectedPathsFromSelectedRows();
        }
    }

    class TreeTableModelAdapter extends AbstractTableModel implements TreeExpansionListener,TreeModelListener
    {
        private static final long serialVersionUID = 1L;

        TreeTableModel treeTableModel;
        public TreeTableModelAdapter(TreeTableModel treeTableModel) {
            this.treeTableModel = treeTableModel;

            tree.addTreeExpansionListener(this);
            // Install a TreeModelListener that can update the table when
            // tree changes. We use delayedFireTableDataChanged as we can
            // not be guaranteed the tree will have finished processing
            // the event before us.
            treeTableModel.addTreeModelListener(this);
        }


        // Implementation of TreeExpansionListener
        public void treeExpanded(TreeExpansionEvent event) {
            int row = tree.getRowForPath(event.getPath());
            if (row + 1 < tree.getRowCount())
                fireTableRowsInserted(row + 1,row + 1);
        }
        public void treeCollapsed(TreeExpansionEvent event) {
            int row = tree.getRowForPath(event.getPath());
            if (row  < getRowCount())
                fireTableRowsDeleted(row  + 1,row + 1);
        }

        // Implementation of TreeModelLstener
        public void treeNodesChanged(TreeModelEvent e) {
            int firstRow = 0;
            int lastRow = tree.getRowCount() -1;
            delayedFireTableRowsUpdated(firstRow,lastRow);
        }

        public void treeNodesInserted(TreeModelEvent e) {
            delayedFireTableDataChanged();
        }

        public void treeNodesRemoved(TreeModelEvent e) {
            delayedFireTableDataChanged();
        }

        public void treeStructureChanged(TreeModelEvent e) {
            delayedFireTableDataChanged();
        }

        // Wrappers, implementing TableModel interface.
        public int getColumnCount() {
            return treeTableModel.getColumnCount();
        }

        public String getColumnName(int column) {
            return treeTableModel.getColumnName(column);
        }

        public Class<?> getColumnClass(int column) {
            return treeTableModel.getColumnClass(column);
        }

        public int getRowCount() {
            return tree.getRowCount();
        }

        private Object nodeForRow(int row) {
            TreePath treePath = tree.getPathForRow(row);
            if (treePath == null)
                return null;
            return treePath.getLastPathComponent();
        }

        public Object getValueAt(int row, int column) {
            Object node = nodeForRow(row);
            if (node == null)
                return null;
            return treeTableModel.getValueAt(node, column);
        }

        public boolean isCellEditable(int row, int column) {
            if (getColumnClass(column) == TreeTableModel.class) {
                return true;
            } else {
                Object node = nodeForRow(row);
                if (node == null)
                    return false;
                return treeTableModel.isCellEditable(node, column);
            }
        }

        public void setValueAt(Object value, int row, int column) {
            Object node = nodeForRow(row);
            if (node == null)
                return;
            treeTableModel.setValueAt(value, node, column);
        }

        /**
         * Invokes fireTableDataChanged after all the pending events have been
         * processed. SwingUtilities.invokeLater is used to handle this.
         */
        protected void delayedFireTableRowsUpdated(int firstRow,int lastRow) {
            SwingUtilities.invokeLater(new UpdateRunnable(firstRow,lastRow));
        }

        class UpdateRunnable implements Runnable {
            int lastRow;
            int firstRow;
            UpdateRunnable(int firstRow,int lastRow) {
                this.firstRow = firstRow;
                this.lastRow = lastRow;
            }
            public void run() {
                fireTableRowsUpdated(firstRow,lastRow);
            }
        }
        /**
         * Invokes fireTableDataChanged after all the pending events have been
         * processed. SwingUtilities.invokeLater is used to handle this.
         */
        protected void delayedFireTableDataChanged() {
            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        fireTableDataChanged();
                    }
                });

        }
    }


    /*
    public void paintComponent(Graphics g) {
        super.paintComponent( g );
        Rectangle r = g.getClipBounds();
        g.setColor( Color.white);
        g.fillRect(0,0, r.width, r.height );
        
    }
    */

}



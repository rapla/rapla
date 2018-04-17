package org.rapla.client.dialog.swing;

import org.rapla.client.dialog.ListView;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Subject;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;

public class SwingListView<T> implements ListView<T> {

    final Subject<T> publisherDoubleClick;
    final Subject<Collection<T>> publisherSelectionChanged;
    final RaplaTree treeSelection;

    @Inject
    public SwingListView(TreeCellRenderer treeCellRenderer, CommandScheduler scheduler)
    {
        publisherDoubleClick = scheduler.createPublisher();
        publisherSelectionChanged = scheduler.createPublisher();
        treeSelection = new RaplaTree();
        treeSelection.setMultiSelect(false);
        treeSelection.getTree().setCellRenderer(treeCellRenderer);
        treeSelection.setMinimumSize(new java.awt.Dimension(300, 200));
        treeSelection.setPreferredSize(new java.awt.Dimension(400, 260));
        final JTree tree = treeSelection.getTree();
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addMouseListener(new MouseAdapter() {
            // End dialog when a leaf is double clicked
            public void mousePressed(MouseEvent e) {
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                if (selPath != null && e.getClickCount() == 2) {
                    final TreeNode lastPathComponent = (TreeNode) selPath.getLastPathComponent();
                    if (lastPathComponent.isLeaf()) {
                        final T userObject = (T)((DefaultMutableTreeNode) lastPathComponent).getUserObject();
                        publisherDoubleClick.onNext( userObject);
                        return;
                    }
                } else if (selPath != null && e.getClickCount() == 1) {
                    final TreeNode lastPathComponent = (TreeNode) selPath.getLastPathComponent();
                    if (lastPathComponent.isLeaf()) {
                        final T userObject = (T)((DefaultMutableTreeNode) lastPathComponent).getUserObject();
                        publisherSelectionChanged.onNext(Collections.singleton(userObject));
                        return;
                    }
                }
                tree.removeSelectionPath(selPath);
            }
        });
    }

    @Override
    public void setObjects(Collection<T> objects) {
        DefaultMutableTreeNode userRoot = new DefaultMutableTreeNode("ROOT");
        for (final T object: objects) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode();
            node.setUserObject(object);
            userRoot.add(node);
        }
        treeSelection.exchangeTreeModel(new DefaultTreeModel(userRoot));
    }

    @Override
    public T getSelected() {
        return (T)treeSelection.getSelectedElement();
    }

    @Override
    public Observable<T> doubleClicked() {
        return publisherDoubleClick;
    }

    @Override
    public Observable<Collection<T>> selectionChanged() {
        return publisherSelectionChanged;
    }

    @Override
    public void setSelected(T object) {
        final Collection<Object> selectedObjects = object != null ? Collections.singleton(object) : Collections.emptyList();
        treeSelection.select(selectedObjects);
    }

    @Override
    public Object getComponent() {
        return treeSelection;
    }
}

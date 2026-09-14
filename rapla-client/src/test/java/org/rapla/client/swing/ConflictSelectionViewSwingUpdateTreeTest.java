package org.rapla.client.swing;

import org.junit.jupiter.api.Test;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.TreeFactory;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.ConflictSelectionView;
import org.rapla.client.swing.i18n.SwingBundleManager;
import org.rapla.client.swing.internal.view.ConflictTreeCellRenderer;
import org.rapla.client.swing.internal.view.TreeItemFactorySwing;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.test.util.HeadlessSwingTestSupport;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the conflict-tree selection-loss bug. When the presenter
 * calls {@code view.updateTree(selectedConflicts, allConflicts)} after a
 * reservation move:
 *
 * <ul>
 *   <li>The previously-selected conflict must remain visually selected on
 *       the rebuilt tree (preserved by {@code RaplaTree.exchangeTreeModel}).
 *   <li>The unguarded {@code TreeSelectionListener} on the JTree must NOT
 *       fire {@code presenter.showConflicts(...)} with an empty selection
 *       during the internal {@code setModel(...)} → that intermediate
 *       fire clears the calendar model's conflict selection and triggers
 *       a spurious calendar refresh.
 * </ul>
 */
class ConflictSelectionViewSwingUpdateTreeTest extends HeadlessSwingTestSupport
{
    @Test
    void selectionAndPresenterStableAcrossUpdateTreeRebuild() throws Exception
    {
        BundleManager bundleManager = new SwingBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        ConflictTreeCellRenderer renderer = new ConflictTreeCellRenderer(i18n, raplaLocale);
        DialogUiFactoryInterface dialogUiFactory = stubDialogUiFactory();

        Conflict c1 = stubConflict("c1");
        Conflict c2 = stubConflict("c2");

        TreeFactory treeFactory = stubTreeFactoryReturning(i18n);

        ConflictSelectionViewSwing view = new ConflictSelectionViewSwing(i18n, treeFactory, dialogUiFactory, renderer);

        AtomicInteger showConflictsCalls = new AtomicInteger();
        ConflictSelectionView.Presenter presenter = stubPresenter(showConflictsCalls);
        view.setPresenter(presenter);

        onEdt(() -> view.updateTree(List.of(), List.of(c1, c2)));
        SwingUtilities.invokeAndWait(() -> {});

        onEdt(() -> selectInTree(view.getTreeSelection().getTree(), c1));
        SwingUtilities.invokeAndWait(() -> {});

        assertTrue(view.getSelectedElements(false).contains(c1),
                "precondition: c1 selected after simulated user click");

        showConflictsCalls.set(0);

        onEdt(() -> view.updateTree(List.of(c1), List.of(c1, c2)));
        SwingUtilities.invokeAndWait(() -> {});

        assertTrue(view.getSelectedElements(false).contains(c1),
                "after rebuilding the conflict tree, the previously-selected conflict must still be selected");
        assertEquals(0, showConflictsCalls.get(),
                "rebuilding the tree with the same selection must not trigger presenter.showConflicts side effects (which clear calendar-model state and publish a spurious refresh)");
    }

    private static void selectInTree(JTree tree, Object userObject)
    {
        TreePath path = findPath((TreeNode) tree.getModel().getRoot(), userObject, new TreePath(tree.getModel().getRoot()));
        if (path == null) throw new IllegalStateException("user-object not found in tree: " + userObject);
        tree.setSelectionPath(path);
    }

    private static TreePath findPath(TreeNode node, Object userObject, TreePath current)
    {
        Object obj = node instanceof javax.swing.tree.DefaultMutableTreeNode ? ((javax.swing.tree.DefaultMutableTreeNode) node).getUserObject() : null;
        if (userObject.equals(obj)) return current;
        for (int i = 0; i < node.getChildCount(); i++)
        {
            TreeNode child = node.getChildAt(i);
            TreePath p = findPath(child, userObject, current.pathByAddingChild(child));
            if (p != null) return p;
        }
        return null;
    }

    private static Conflict stubConflict(String id)
    {
        ReferenceInfo<Conflict> ref = new ReferenceInfo<>(id, Conflict.class);
        java.time.LocalDateTime startDate = java.time.LocalDateTime.of(2026, 6, 10, 9, 0);
        return (Conflict) Proxy.newProxyInstance(
                Conflict.class.getClassLoader(),
                new Class[] { Conflict.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":               return id;
                        case "getReference":        return ref;
                        case "checkEnabled":        return true;
                        case "getStartDate":        return startDate;
                        case "getReservation1Name": return "Reservation A " + id;
                        case "getReservation2Name": return "Reservation B " + id;
                        case "getRepeatingType1":   return null;
                        case "getRepeatingType2":   return null;
                        case "equals":              return args[0] instanceof Conflict && id.equals(((Conflict) args[0]).getId());
                        case "hashCode":            return id.hashCode();
                        case "toString":            return "Conflict[" + id + "]";
                        default:                    return null;
                    }
                });
    }

    private static TreeFactory stubTreeFactoryReturning(RaplaResources i18n)
    {
        TreeItemFactorySwing nodeFactory = new TreeItemFactorySwing(i18n);
        return (TreeFactory) Proxy.newProxyInstance(
                TreeFactory.class.getClassLoader(),
                new Class[] { TreeFactory.class },
                (proxy, method, args) ->
                {
                    if ("createConflictModel".equals(method.getName()))
                    {
                        @SuppressWarnings("unchecked")
                        java.util.Collection<Conflict> conflicts = (java.util.Collection<Conflict>) args[0];
                        RaplaTreeNode root = nodeFactory.createNode("root");
                        RaplaTreeNode bucket = nodeFactory.createNode("conflictUC");
                        root.add(bucket);
                        for (Conflict c : conflicts)
                        {
                            bucket.add(nodeFactory.createNode(c));
                        }
                        return root;
                    }
                    return null;
                });
    }

    private static DialogUiFactoryInterface stubDialogUiFactory()
    {
        return (DialogUiFactoryInterface) Proxy.newProxyInstance(
                DialogUiFactoryInterface.class.getClassLoader(),
                new Class[] { DialogUiFactoryInterface.class },
                (proxy, method, args) -> null);
    }

    private static ConflictSelectionView.Presenter stubPresenter(AtomicInteger showConflictsCalls)
    {
        return (ConflictSelectionView.Presenter) Proxy.newProxyInstance(
                ConflictSelectionView.Presenter.class.getClassLoader(),
                new Class[] { ConflictSelectionView.Presenter.class },
                (proxy, method, args) ->
                {
                    if ("showConflicts".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof PopupContext)
                    {
                        showConflictsCalls.incrementAndGet();
                    }
                    return null;
                });
    }
}

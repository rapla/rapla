package org.rapla.client.swing.toolkit;

import org.junit.jupiter.api.Test;
import org.rapla.test.util.HeadlessSwingTestSupport;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test: the same user object can appear under multiple parent
 * nodes (TreeFactoryImpl creates one node per categorization value, e.g. the
 * same room under "Gebäude" and "Studiengang"). Selecting the object must
 * highlight EVERY node carrying it, not just the first one in DFS order.
 */
class RaplaTreeDuplicateSelectionTest extends HeadlessSwingTestSupport
{
    private static final Object ROOM = new Object()
    {
        @Override
        public String toString()
        {
            return "room1";
        }
    };
    private static final Object OTHER = new Object()
    {
        @Override
        public String toString()
        {
            return "room2";
        }
    };

    private static DefaultTreeModel modelWithDuplicateRoom()
    {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode catA = new DefaultMutableTreeNode("Gebäude A");
        DefaultMutableTreeNode catB = new DefaultMutableTreeNode("Studiengang B");
        root.add(catA);
        root.add(catB);
        catA.add(new DefaultMutableTreeNode(ROOM));
        catB.add(new DefaultMutableTreeNode(ROOM));
        catB.add(new DefaultMutableTreeNode(OTHER));
        return new DefaultTreeModel(root);
    }

    private static Set<Object> selectedUserObjects(RaplaTree tree)
    {
        Set<Object> result = new HashSet<>();
        TreePath[] paths = tree.getTree().getSelectionPaths();
        if (paths != null)
        {
            for (TreePath path : paths)
            {
                result.add(((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject());
            }
        }
        return result;
    }

    @Test
    void selectHighlightsEveryNodeOfADuplicatedUserObject() throws Exception
    {
        onEdt(() -> {
            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(modelWithDuplicateRoom());

            tree.select(Collections.singletonList(ROOM));

            TreePath[] paths = tree.getTree().getSelectionPaths();
            assertEquals(2, paths == null ? 0 : paths.length,
                    "both occurrences of the duplicated object must be selected");
            assertEquals(Collections.singleton(ROOM), selectedUserObjects(tree));
        });
    }

    @Test
    void exchangeTreeModelKeepsSelectionOnAllDuplicates() throws Exception
    {
        onEdt(() -> {
            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(modelWithDuplicateRoom());
            tree.select(Arrays.asList(ROOM, OTHER));
            tree.expandAll();

            tree.exchangeTreeModel(modelWithDuplicateRoom());

            TreePath[] paths = tree.getTree().getSelectionPaths();
            assertEquals(3, paths == null ? 0 : paths.length,
                    "model exchange must restore selection on every duplicate node");
            assertEquals(new HashSet<>(Arrays.asList(ROOM, OTHER)), selectedUserObjects(tree));
        });
    }

    @Test
    void programmaticSelectAndModelExchangeDoNotScrollTheViewport() throws Exception
    {
        onEdt(() -> {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
            for (int cat = 0; cat < 3; cat++)
            {
                DefaultMutableTreeNode catNode = new DefaultMutableTreeNode("cat" + cat);
                root.add(catNode);
                for (int i = 0; i < 20; i++)
                {
                    catNode.add(new DefaultMutableTreeNode("res-" + cat + "-" + i));
                }
            }
            DefaultMutableTreeNode lastLeaf = new DefaultMutableTreeNode(ROOM);
            ((DefaultMutableTreeNode) root.getChildAt(2)).add(lastLeaf);

            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(new DefaultTreeModel(root));
            tree.expandAll();
            tree.setSize(200, 100);
            tree.validate();
            java.awt.Point bottom = new java.awt.Point(0,
                    Math.max(0, tree.getTree().getPreferredSize().height - 100));
            tree.getViewport().setViewPosition(bottom);

            tree.select(Collections.singletonList(ROOM));
            assertEquals(bottom, tree.getViewport().getViewPosition(),
                    "select() must not scroll the viewport");

            DefaultMutableTreeNode root2 = (DefaultMutableTreeNode) new DefaultTreeModel(root).getRoot();
            tree.exchangeTreeModel(new DefaultTreeModel(root2));
            assertEquals(bottom, tree.getViewport().getViewPosition(),
                    "exchangeTreeModel() must not scroll the viewport");
        });
    }

    private static TreePath pathOfNthOccurrence(RaplaTree tree, Object userObject, int n)
    {
        int seen = 0;
        java.util.Iterator<javax.swing.tree.TreeNode> it =
                new RaplaTree.TreeIterator((javax.swing.tree.TreeNode) tree.getTree().getModel().getRoot());
        while (it.hasNext())
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) it.next();
            if (node.getUserObject() == userObject && seen++ == n)
            {
                return new TreePath(node.getPath());
            }
        }
        throw new IllegalArgumentException("occurrence not found");
    }

    @Test
    void userClickOnOneDuplicateSelectsAllOccurrences() throws Exception
    {
        onEdt(() -> {
            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(modelWithDuplicateRoom());

            // simulate a user click on the SECOND occurrence
            tree.getTree().setSelectionPath(pathOfNthOccurrence(tree, ROOM, 1));

            TreePath[] paths = tree.getTree().getSelectionPaths();
            assertEquals(2, paths == null ? 0 : paths.length,
                    "clicking one occurrence must highlight every occurrence");
            assertEquals(Collections.singleton(ROOM), selectedUserObjects(tree));
        });
    }

    @Test
    void firstClickOnDuplicateLeavesTwinParentCollapsed() throws Exception
    {
        onEdt(() -> {
            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(modelWithDuplicateRoom());
            // user state: twin's parent (catA = "Gebäude A") collapsed, clicked branch expanded
            TreePath second = pathOfNthOccurrence(tree, ROOM, 1);
            TreePath twinParent = pathOfNthOccurrence(tree, ROOM, 0).getParentPath();
            tree.getTree().collapsePath(twinParent);
            tree.getTree().expandPath(second.getParentPath());

            // first click: the twin gets selected but its collapsed parent must stay
            // collapsed — expanding it inserts rows and visually shifts the tree away
            // from the clicked node
            tree.getTree().setSelectionPath(second);

            assertEquals(false, tree.getTree().isExpanded(twinParent),
                    "mirror selection must not expand the twin's collapsed parent");
            assertEquals(Collections.singleton(ROOM), selectedUserObjects(tree));

            // the state round-trip re-applies the selection without expanding either
            tree.select(Collections.singletonList(ROOM), false);
            assertEquals(false, tree.getTree().isExpanded(twinParent),
                    "selection re-apply must not expand collapsed parents");
            assertEquals(Collections.singleton(ROOM), selectedUserObjects(tree));
        });
    }

    @Test
    void clickOnDuplicateLeavesAnchorOnTheClickedVisibleRow() throws Exception
    {
        onEdt(() -> {
            // ROOM occurs under catA (clicked, visible) and catB (twin, collapsed).
            // The clicked occurrence is DFS-first so the twin is the DFS-last path.
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
            DefaultMutableTreeNode catA = new DefaultMutableTreeNode("Gebäude A");
            DefaultMutableTreeNode catB = new DefaultMutableTreeNode("Studiengang B");
            root.add(catA);
            root.add(catB);
            DefaultMutableTreeNode clicked = new DefaultMutableTreeNode(ROOM);
            catA.add(clicked);
            catA.add(new DefaultMutableTreeNode("x"));
            catB.add(new DefaultMutableTreeNode(ROOM));

            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(new DefaultTreeModel(root));
            tree.getTree().expandPath(new TreePath(catA.getPath()));
            tree.getTree().collapsePath(new TreePath(catB.getPath()));

            TreePath clickedPath = new TreePath(clicked.getPath());
            // simulate the plain user click
            tree.getTree().setSelectionPath(clickedPath);

            // BasicTreeUI's shift-click extends from getAnchorSelectionPath(); when that
            // path is off-screen (row -1) it silently falls back to single-selection,
            // which is exactly the "shift-select stopped working" symptom.
            assertEquals(clickedPath, tree.getTree().getAnchorSelectionPath(),
                    "the anchor must stay on the clicked node, not jump to a collapsed twin");
            int anchorRow = tree.getTree().getRowForPath(tree.getTree().getAnchorSelectionPath());
            assertEquals(true, anchorRow >= 0,
                    "the anchor must be a visible row so shift-click can extend the range");
        });
    }

    @Test
    void shiftExtendAfterClickingDuplicateSelectsTheRange() throws Exception
    {
        onEdt(() -> {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
            DefaultMutableTreeNode catA = new DefaultMutableTreeNode("Gebäude A");
            DefaultMutableTreeNode catB = new DefaultMutableTreeNode("Studiengang B");
            root.add(catA);
            root.add(catB);
            DefaultMutableTreeNode clicked = new DefaultMutableTreeNode(ROOM);
            DefaultMutableTreeNode x = new DefaultMutableTreeNode("x");
            DefaultMutableTreeNode y = new DefaultMutableTreeNode("y");
            catA.add(clicked);
            catA.add(x);
            catA.add(y);
            catB.add(new DefaultMutableTreeNode(ROOM));

            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(new DefaultTreeModel(root));
            tree.getTree().expandPath(new TreePath(catA.getPath()));
            tree.getTree().collapsePath(new TreePath(catB.getPath()));

            // plain click on ROOM in catA
            tree.getTree().setSelectionPath(new TreePath(clicked.getPath()));

            // shift-click on y, mimicking BasicTreeUI: extend from the anchor's row to
            // the clicked row. This is the gesture that was selecting only one node.
            int anchorRow = tree.getTree().getRowForPath(tree.getTree().getAnchorSelectionPath());
            int targetRow = tree.getTree().getRowForPath(new TreePath(y.getPath()));
            tree.getTree().setSelectionInterval(Math.min(anchorRow, targetRow), Math.max(anchorRow, targetRow));

            assertEquals(new HashSet<>(Arrays.asList(ROOM, "x", "y")), selectedUserObjects(tree),
                    "shift-extend from the clicked duplicate must select the whole range");
        });
    }

    private static void mousePress(RaplaTree tree, TreePath path, boolean shift)
    {
        javax.swing.JTree jt = tree.getTree();
        int row = jt.getRowForPath(path);
        java.awt.Rectangle b = jt.getRowBounds(row);
        int mods = java.awt.event.InputEvent.BUTTON1_DOWN_MASK
                | (shift ? java.awt.event.InputEvent.SHIFT_DOWN_MASK : 0);
        java.awt.event.MouseEvent me = new java.awt.event.MouseEvent(jt,
                java.awt.event.MouseEvent.MOUSE_PRESSED, 1L, mods,
                b.x + 2, b.y + b.height / 2, 1, false, java.awt.event.MouseEvent.BUTTON1);
        jt.dispatchEvent(me);
    }

    @Test
    void shiftClickingTheAlreadySelectedDuplicateKeepsItSelected() throws Exception
    {
        onEdt(() -> {
            // ROOM occurs in catA (visible) and catB (collapsed twin). Shift-clicking the
            // already-selected occurrence removes only the collapsed twin from the model;
            // the deselect-cascade must NOT treat that as the user deselecting ROOM.
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
            DefaultMutableTreeNode catA = new DefaultMutableTreeNode("Gebäude A");
            DefaultMutableTreeNode catB = new DefaultMutableTreeNode("Studiengang B");
            root.add(catA);
            root.add(catB);
            DefaultMutableTreeNode clicked = new DefaultMutableTreeNode(ROOM);
            catA.add(clicked);
            catA.add(new DefaultMutableTreeNode("x"));
            catB.add(new DefaultMutableTreeNode(ROOM));

            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(new DefaultTreeModel(root));
            tree.getTree().setSize(200, 200);
            tree.getTree().validate();
            tree.getTree().expandPath(new TreePath(catA.getPath()));
            tree.getTree().collapsePath(new TreePath(catB.getPath()));

            TreePath clickedPath = new TreePath(clicked.getPath());
            mousePress(tree, clickedPath, false);
            assertEquals(Collections.singleton(ROOM), selectedUserObjects(tree),
                    "plain click selects the room");

            // shift-click the same row — a no-op-intent gesture that must not clear it
            mousePress(tree, clickedPath, true);
            assertEquals(Collections.singleton(ROOM), selectedUserObjects(tree),
                    "shift-clicking the already-selected duplicate must keep it selected");
        });
    }

    @Test
    void ctrlClickingASelectedDuplicateStillDeselectsAllOccurrences() throws Exception
    {
        onEdt(() -> {
            // The deselect-cascade must survive: ctrl-clicking one occurrence off still
            // removes every occurrence (the symmetric twin of select-one-selects-all).
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
            DefaultMutableTreeNode catA = new DefaultMutableTreeNode("Gebäude A");
            DefaultMutableTreeNode catB = new DefaultMutableTreeNode("Studiengang B");
            root.add(catA);
            root.add(catB);
            DefaultMutableTreeNode clicked = new DefaultMutableTreeNode(ROOM);
            catA.add(clicked);
            catB.add(new DefaultMutableTreeNode(ROOM));

            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(new DefaultTreeModel(root));
            tree.getTree().setSize(200, 200);
            tree.getTree().validate();
            tree.getTree().expandPath(new TreePath(catA.getPath()));
            tree.getTree().expandPath(new TreePath(catB.getPath()));

            tree.select(Collections.singletonList(ROOM));
            assertEquals(2, tree.getTree().getSelectionCount(), "both occurrences selected");

            // ctrl-click one occurrence off
            javax.swing.JTree jt = tree.getTree();
            TreePath clickedPath = new TreePath(clicked.getPath());
            int row = jt.getRowForPath(clickedPath);
            java.awt.Rectangle b = jt.getRowBounds(row);
            java.awt.event.MouseEvent me = new java.awt.event.MouseEvent(jt,
                    java.awt.event.MouseEvent.MOUSE_PRESSED, 1L,
                    java.awt.event.InputEvent.BUTTON1_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK,
                    b.x + 2, b.y + b.height / 2, 1, false, java.awt.event.MouseEvent.BUTTON1);
            jt.dispatchEvent(me);

            assertEquals(Collections.emptySet(), selectedUserObjects(tree),
                    "ctrl-clicking one occurrence off must deselect every occurrence");
        });
    }

    @Test
    void userDeselectOfOneDuplicateDeselectsAllOccurrences() throws Exception
    {
        onEdt(() -> {
            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(modelWithDuplicateRoom());
            tree.select(Arrays.asList(ROOM, OTHER));

            // simulate a ctrl-click toggle-off on the first occurrence
            tree.getTree().removeSelectionPath(pathOfNthOccurrence(tree, ROOM, 0));

            assertEquals(Collections.singleton(OTHER), selectedUserObjects(tree),
                    "deselecting one occurrence must deselect every occurrence");
        });
    }

    private static DefaultTreeModel bigModel(int extraTopNodes)
    {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        for (int i = 0; i < extraTopNodes; i++)
        {
            root.add(new DefaultMutableTreeNode("extra-" + i));
        }
        for (int cat = 0; cat < 3; cat++)
        {
            DefaultMutableTreeNode catNode = new DefaultMutableTreeNode("cat" + cat);
            root.add(catNode);
            for (int i = 0; i < 20; i++)
            {
                catNode.add(new DefaultMutableTreeNode("res-" + cat + "-" + i));
            }
        }
        return new DefaultTreeModel(root);
    }

    private static TreePath pathTo(RaplaTree tree, Object userObject)
    {
        java.util.Iterator<javax.swing.tree.TreeNode> it =
                new RaplaTree.TreeIterator((javax.swing.tree.TreeNode) tree.getTree().getModel().getRoot());
        while (it.hasNext())
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) it.next();
            if (userObject.equals(node.getUserObject()))
            {
                return new TreePath(node.getPath());
            }
        }
        throw new IllegalArgumentException("not found: " + userObject);
    }

    @Test
    void clickedRowStaysAtSameViewportOffsetAcrossModelExchange() throws Exception
    {
        RaplaTree tree = new RaplaTree();
        onEdt(() -> {
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(bigModel(0));
            tree.expandAll();
            tree.setSize(200, 100);
            tree.validate();

            TreePath clicked = pathTo(tree, "res-2-10");
            java.awt.Rectangle bounds = tree.getTree().getPathBounds(clicked);
            tree.getViewport().setViewPosition(new java.awt.Point(0, bounds.y - 30));
            // user click — captures the anchor (row at offset 30 inside the viewport)
            tree.getTree().setSelectionPath(clicked);

            // rebuild with extra nodes inserted above — shifts all rows down
            tree.exchangeTreeModel(bigModel(5));
        });
        // anchor restore runs via invokeLater — flush the EDT queue
        onEdt(() -> {});
        onEdt(() -> {
            TreePath clickedAfter = pathTo(tree, "res-2-10");
            java.awt.Rectangle after = tree.getTree().getPathBounds(clickedAfter);
            int offset = after.y - tree.getViewport().getViewPosition().y;
            assertEquals(30, offset, "clicked row must sit at the same viewport offset after the rebuild");
        });
    }

    private static DefaultTreeModel prunedModel()
    {
        // search-pruned variant of bigModel: cat0/cat1 dropped entirely, cat2 reduced
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode cat2 = new DefaultMutableTreeNode("cat2");
        root.add(cat2);
        cat2.add(new DefaultMutableTreeNode("res-2-10"));
        return new DefaultTreeModel(root);
    }

    private static boolean isExpanded(RaplaTree tree, Object userObject)
    {
        return tree.getTree().isExpanded(pathTo(tree, userObject));
    }

    @Test
    void expansionSurvivesSearchPruneAndRestore() throws Exception
    {
        onEdt(() -> {
            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(bigModel(0));
            // user expands two categories
            tree.getTree().expandPath(pathTo(tree, "cat0"));
            tree.getTree().expandPath(pathTo(tree, "cat2"));

            // search prunes the tree (cat0 vanishes entirely), then the term is cleared
            tree.exchangeTreeModel(prunedModel());
            tree.exchangeTreeModel(bigModel(0));

            assertEquals(true, isExpanded(tree, "cat0"),
                    "expansion must survive a search prune that removed the branch");
            assertEquals(true, isExpanded(tree, "cat2"), "cat2 expansion must survive");
            assertEquals(false, isExpanded(tree, "cat1"),
                    "never-expanded branches stay collapsed");
        });
    }

    @Test
    void userCollapseIsRememberedAcrossRebuild() throws Exception
    {
        onEdt(() -> {
            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(bigModel(0));
            tree.getTree().expandPath(pathTo(tree, "cat0"));
            tree.getTree().collapsePath(pathTo(tree, "cat0"));

            tree.exchangeTreeModel(bigModel(0));

            assertEquals(false, isExpanded(tree, "cat0"),
                    "a branch the user collapsed must stay collapsed after a rebuild");
        });
    }

    @Test
    void selectedElementsCollapseDuplicatesToOneObject() throws Exception
    {
        onEdt(() -> {
            RaplaTree tree = new RaplaTree();
            tree.setMultiSelect(true);
            tree.exchangeTreeModel(modelWithDuplicateRoom());

            tree.select(Collections.singletonList(ROOM));

            List<Object> elements = tree.getSelectedElements();
            assertEquals(Collections.singletonList(ROOM), elements);
        });
    }
}

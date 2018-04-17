package org.rapla.client.swing.internal.view;

import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class DelegatingTreeSelectionModel extends DefaultTreeSelectionModel
{
    Predicate<TreePath> isSelectable;
    public DelegatingTreeSelectionModel(Predicate<TreePath> isSelectable)
    {
        this.isSelectable = isSelectable;
    }


    private static final long serialVersionUID = 1L;

    private TreePath[] getSelectablePaths(TreePath[] pathList)
    {
        List<TreePath> result = new ArrayList<>(pathList.length);
        for (TreePath treePath : pathList)
        {
            if (isSelectable.test(treePath))
            {
                result.add(treePath);
            }
        }
        return result.toArray(new TreePath[result.size()]);
    }

    @Override
    public void setSelectionPath(TreePath path)
    {
        if (isSelectable.test(path))
        {
            super.setSelectionPath(path);
        }
    }

    @Override
    public void setSelectionPaths(TreePath[] paths)
    {
        paths = getSelectablePaths(paths);
        super.setSelectionPaths(paths);
    }

    @Override
    public void addSelectionPath(TreePath path)
    {
        if (isSelectable.test(path))
        {
            super.addSelectionPath(path);
        }
    }

    @Override
    public void addSelectionPaths(TreePath[] paths)
    {
        paths = getSelectablePaths(paths);
        super.addSelectionPaths(paths);
    }

}

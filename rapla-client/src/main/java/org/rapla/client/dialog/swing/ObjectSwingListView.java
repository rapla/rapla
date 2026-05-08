package org.rapla.client.dialog.swing;

import org.rapla.client.dialog.ListView;
import org.rapla.scheduler.CommandScheduler;

import org.springframework.beans.factory.annotation.Autowired;
import javax.swing.tree.TreeCellRenderer;

// TODO Workaround until Restinject-Generator can handle proper Generics
@org.springframework.stereotype.Service
public class ObjectSwingListView extends SwingListView<Object>
{
    @Autowired
    public ObjectSwingListView(TreeCellRenderer treeFactory, CommandScheduler scheduler)
    {
        super(treeFactory, scheduler);
    }
}

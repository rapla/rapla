package org.rapla.client.dialog.swing;

import org.rapla.client.dialog.ListView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.CommandScheduler;

import javax.inject.Inject;
import javax.swing.tree.TreeCellRenderer;

// TODO Workaround until Restinject-Generator can handle proper Generics
@DefaultImplementation(of=ListView.class,context = InjectionContext.swing)
public class ObjectSwingListView extends SwingListView<Object>
{
    @Inject
    public ObjectSwingListView(TreeCellRenderer treeFactory, CommandScheduler scheduler)
    {
        super(treeFactory, scheduler);
    }
}

package org.rapla.plugin.externaleventimport.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.TreeFactory;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.CommandAbortedException;
import org.rapla.client.swing.internal.view.RaplaSwingTreeModel;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.entities.domain.Allocatable;
import org.rapla.plugin.externaleventimport.ExternalEventImportMetadata;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportController;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportResources;
import org.rapla.scheduler.Promise;
import org.springframework.beans.factory.annotation.Autowired;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.tree.TreeModel;
import java.awt.BorderLayout;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Allocatable-picker dialog used when the user has no calendar pre-selection.
 * Renders a tree of allocatables of the type declared by {@code metadata.selectableAllocatableTypeKey}.
 * No domain knowledge — the type key and label come from server metadata.
 */
public class ExternalEventImportAllocatableSelectionDialog
{
    private final DialogUiFactoryInterface dialogUiFactory;
    private final ExternalEventImportResources resources;
    private final RaplaResources raplaResources;
    private final TreeFactory treeFactory;

    @Autowired
    public ExternalEventImportAllocatableSelectionDialog(DialogUiFactoryInterface dialogUiFactory, ExternalEventImportResources resources,
            RaplaResources raplaResources, TreeFactory treeFactory)
    {
        this.dialogUiFactory = dialogUiFactory;
        this.resources = resources;
        this.raplaResources = raplaResources;
        this.treeFactory = treeFactory;
    }

    public Promise<Collection<Allocatable>> show(ExternalEventImportMetadata metadata, Collection<Allocatable> available, Collection<Allocatable> preselected)
    {
        String[] options = { resources.getString("loadEvents"), raplaResources.getString("cancel") };
        JPanel content = new JPanel(new BorderLayout());
        String leafLabel = ExternalEventImportController.leafLevelLabel(metadata, "item");
        String header = MessageFormat.format(resources.getString("chooseSelectionHeader"), leafLabel);
        content.add(new JLabel(header), BorderLayout.NORTH);

        RaplaTreeNode classifiableModel = treeFactory.createClassifiableModel(available.toArray(new Allocatable[0]), false);
        TreeModel model = new RaplaSwingTreeModel(classifiableModel);
        RaplaTree raplaTree = new RaplaTree();
        raplaTree.setMultiSelect(true);
        RaplaTree.exchangeTreeModel(model, raplaTree.getTree());
        raplaTree.select((Collection) preselected);
        content.add(raplaTree, BorderLayout.CENTER);

        PopupContext popupContext = dialogUiFactory.createPopupContext(null);
        content.setSize(600, 600);
        DialogInterface di = dialogUiFactory.createContentDialog(popupContext, content, options);
        di.setDefault(0);
        String title = MessageFormat.format(resources.getString("import.title"),
                metadata.getSourceName() == null ? "" : metadata.getSourceName());
        di.setTitle(title);

        return di.start(false).thenApply(selectedIndex -> {
            if (selectedIndex == 0)
            {
                List<Object> elements = raplaTree.getSelectedElements();
                List<Allocatable> picked = new ArrayList<>(elements.size());
                for (Object e : elements) picked.add((Allocatable) e);
                return picked;
            }
            throw new CommandAbortedException("close");
        });
    }
}

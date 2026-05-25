package org.rapla.client.swing.internal.adminpanels;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.extensionpoints.SystemOptionPanel;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.swing.OptionPanel;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.adminpanels.ActionResult;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.adminpanels.PanelSummary;
import org.rapla.plugin.adminpanels.PreferencesAdminService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Server-driven Settings/Admin dialog. Mirrors {@code PreferencesEditUI}'s
 *  layout (tree on the left, form on the right) but is fully data-driven from
 *  {@link PanelSummary} / {@link PanelDefinition} records returned by
 *  {@link PreferencesAdminService}.
 *
 *  <p><b>Legacy bridge (PRD 020 Phase 4)</b>: this dialog is the single
 *  entry point for both new server-driven panels AND the existing Swing
 *  {@link UserOptionPanel} / {@link SystemOptionPanel} / {@link PluginOptionPanel}
 *  impls. The tree is the union of both sets:
 *  <ul>
 *    <li>{@code PER_USER} scope → server PER_USER panels + {@code UserOptionPanel}s</li>
 *    <li>{@code SYSTEM} scope → server SYSTEM panels + {@code SystemOptionPanel}s + {@code PluginOptionPanel}s</li>
 *  </ul>
 *  Server panels save through {@code api.savePanel(...)}. Legacy panels save
 *  through {@code OptionPanel.commit()} + {@code RaplaFacade.dispatch(...)}
 *  on a single editable {@link Preferences} instance shared across all legacy
 *  selections in the dialog session. Legacy entries land here without code
 *  change; migrating one to a {@code PreferencesPanel} bean removes its
 *  legacy class. */
@Service
public class ServerDrivenSettingsDialog
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerDrivenSettingsDialog.class);
    private final PreferencesAdminService api;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaResources i18n;
    private final ClientFacade clientFacade;
    private final Supplier<Set<UserOptionPanel>> userOptionPanels;
    private final Supplier<Set<SystemOptionPanel>> systemOptionPanels;
    private final Map<String, Supplier<PluginOptionPanel>> pluginOptionPanels;
    private final TreeCellRenderer treeCellRenderer;

    @Autowired
    public ServerDrivenSettingsDialog(PreferencesAdminService api,
            DialogUiFactoryInterface dialogUiFactory,
            RaplaResources i18n,
            ClientFacade clientFacade,
            Supplier<Set<UserOptionPanel>> userOptionPanels,
            Supplier<Set<SystemOptionPanel>> systemOptionPanels,
            Map<String, Supplier<PluginOptionPanel>> pluginOptionPanels,
            TreeCellRenderer treeCellRenderer)
    {
        this.api = api;
        this.dialogUiFactory = dialogUiFactory;
        this.i18n = i18n;
        this.clientFacade = clientFacade;
        this.userOptionPanels = userOptionPanels;
        this.systemOptionPanels = systemOptionPanels;
        this.pluginOptionPanels = pluginOptionPanels;
        this.treeCellRenderer = treeCellRenderer;
    }

    public void show(PopupContext popupContext, PanelScope scope) throws RaplaException
    {
        List<PanelSummary> serverSummaries = api.listPanels(scope);
        List<LegacyEntry> legacyEntries = collectLegacyPanels(scope);

        if (serverSummaries.isEmpty() && legacyEntries.isEmpty())
        {
            JLabel emptyMsg = new JLabel(i18n.getString("nothing_selected"));
            DialogInterface dialog = dialogUiFactory.createContentDialog(popupContext, emptyMsg, new String[]{i18n.getString("ok")});
            dialog.start(true);
            return;
        }

        Preferences editablePrefs = obtainEditablePreferences(scope);
        JComponent content = buildContent(serverSummaries, legacyEntries, editablePrefs);
        DialogInterface dialog = dialogUiFactory.createContentDialog(popupContext, content,
                new String[]{i18n.getString("close")});
        dialog.setTitle(scope == PanelScope.SYSTEM ? "Admin Settings" : i18n.getString("options"));
        dialog.start(true);
    }

    /** One editable {@link Preferences} object shared across every legacy
     *  selection — legacy panels mutate it in place via {@code commit()},
     *  and the per-panel "Save" button persists the whole thing in one
     *  {@code dispatch} call. */
    private Preferences obtainEditablePreferences(PanelScope scope) throws RaplaException
    {
        RaplaFacade facade = clientFacade.getRaplaFacade();
        if (scope == PanelScope.SYSTEM)
        {
            return facade.edit(facade.getSystemPreferences());
        }
        User user = clientFacade.getUser();
        return facade.edit(facade.getPreferences(user));
    }

    private List<LegacyEntry> collectLegacyPanels(PanelScope scope) throws RaplaException
    {
        List<LegacyEntry> result = new ArrayList<>();
        Locale locale = i18n.getLocale();
        if (scope == PanelScope.PER_USER)
        {
            for (UserOptionPanel p : userOptionPanels.get())
            {
                if (p.isEnabled()) result.add(new LegacyEntry(p, List.of(), p.getName(locale)));
            }
        }
        else
        {
            for (SystemOptionPanel p : systemOptionPanels.get())
            {
                result.add(new LegacyEntry(p, List.of("Admin"), p.getName(locale)));
            }
            for (Supplier<PluginOptionPanel> sup : pluginOptionPanels.values())
            {
                PluginOptionPanel p = sup.get();
                result.add(new LegacyEntry(p, List.of("Plugins"), p.getName(locale)));
            }
        }
        result.sort(Comparator.comparing(e -> String.join("/", e.path) + "/" + e.title));
        return result;
    }

    private JComponent buildContent(List<PanelSummary> serverSummaries,
            List<LegacyEntry> legacyEntries, Preferences editablePrefs)
    {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
        addServerLeaves(root, serverSummaries);
        addLegacyLeaves(root, legacyEntries);

        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setCellRenderer(treeCellRenderer);
        tree.setRootVisible(false);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        for (int i = tree.getRowCount() - 1; i >= 0; i--) tree.expandRow(i);

        JPanel rightPanel = new JPanel(new CardLayout());
        rightPanel.add(new JLabel(i18n.getString("nothing_selected")), "empty");

        TreeSelectionListener listener = e ->
        {
            TreePath path = e.getNewLeadSelectionPath();
            if (path == null) return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object payload = node.getUserObject();
            if (payload instanceof PanelSummary summary)
            {
                renderServerPanelInto(rightPanel, summary);
            }
            else if (payload instanceof LegacyEntry entry)
            {
                renderLegacyPanelInto(rightPanel, entry, editablePrefs);
            }
        };
        tree.getSelectionModel().addTreeSelectionListener(listener);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), rightPanel);
        split.setDividerLocation(260);
        split.setPreferredSize(new Dimension(900, 600));

        DefaultMutableTreeNode firstLeaf = findFirstLeaf(root);
        if (firstLeaf != null) tree.setSelectionPath(new TreePath(firstLeaf.getPath()));
        return split;
    }

    private void addServerLeaves(DefaultMutableTreeNode root, List<PanelSummary> summaries)
    {
        List<PanelSummary> sorted = new ArrayList<>(summaries);
        sorted.sort(Comparator.comparing(s -> String.join("/", s.path()) + "/" + s.title()));
        for (PanelSummary summary : sorted)
        {
            DefaultMutableTreeNode parent = root;
            for (String segment : summary.path()) parent = childNamed(parent, segment);
            DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(summary)
            {
                @Override public String toString() { return summary.title(); }
            };
            parent.add(leaf);
        }
    }

    private void addLegacyLeaves(DefaultMutableTreeNode root, List<LegacyEntry> entries)
    {
        for (LegacyEntry entry : entries)
        {
            DefaultMutableTreeNode parent = root;
            for (String segment : entry.path) parent = childNamed(parent, segment);
            DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(entry)
            {
                @Override public String toString() { return entry.title; }
            };
            parent.add(leaf);
        }
    }

    private void renderServerPanelInto(JPanel rightPanel, PanelSummary summary)
    {
        rightPanel.removeAll();
        try
        {
            PanelDefinition def = api.getPanel(summary.id());
            PanelRenderer renderer = new PanelRenderer(def,
                    (actionId, currentValues) -> invokeAction(summary.id(), actionId, currentValues),
                    values -> saveServerPanel(rightPanel, summary, values),
                    i18n.getString("save"));
            // PanelRenderer owns the JScrollPane internally so the footer
            // (Save button + action buttons) stays pinned and the scrollable
            // body tracks viewport width.
            rightPanel.add(renderer.getComponent(), "current");
        }
        catch (Exception e)
        {
            LOGGER.error("Could not load panel {}", summary.id(), e);
            rightPanel.add(new JLabel("Could not load panel: " + e.getMessage()), "current");
        }
        ((CardLayout) rightPanel.getLayout()).show(rightPanel, "current");
        rightPanel.revalidate();
        rightPanel.repaint();
    }

    private void saveServerPanel(JPanel rightPanel, PanelSummary summary, java.util.Map<String, Object> values)
    {
        try
        {
            api.savePanel(summary.id(), values);
            javax.swing.JOptionPane.showMessageDialog(rightPanel, i18n.getString("save"));
        }
        catch (Exception ex)
        {
            LOGGER.error("Save failed for panel {}", summary.id(), ex);
            javax.swing.JOptionPane.showMessageDialog(rightPanel, ex.getMessage(),
                    i18n.getString("error"), javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renderLegacyPanelInto(JPanel rightPanel, LegacyEntry entry, Preferences editablePrefs)
    {
        rightPanel.removeAll();
        try
        {
            entry.panel.setPreferences(editablePrefs);
            entry.panel.show();
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(new JScrollPane((JComponent) entry.panel.getComponent()), BorderLayout.CENTER);
            wrapper.add(buildLegacySaveBar(entry, editablePrefs), BorderLayout.SOUTH);
            rightPanel.add(wrapper, "current");
        }
        catch (Exception e)
        {
            LOGGER.error("Could not load legacy panel {}", entry.title, e);
            rightPanel.add(new JLabel("Could not load: " + e.getMessage()), "current");
        }
        ((CardLayout) rightPanel.getLayout()).show(rightPanel, "current");
        rightPanel.revalidate();
        rightPanel.repaint();
    }

    private JComponent buildLegacySaveBar(LegacyEntry entry, Preferences editablePrefs)
    {
        JPanel bar = new JPanel(new BorderLayout());
        javax.swing.JButton save = new javax.swing.JButton(i18n.getString("save"));
        save.addActionListener(e ->
        {
            try
            {
                entry.panel.commit();
                clientFacade.getRaplaFacade().dispatch(
                        Collections.singleton(editablePrefs),
                        Collections.emptySet());
                javax.swing.JOptionPane.showMessageDialog(bar, i18n.getString("save"));
            }
            catch (Exception ex)
            {
                LOGGER.error("Save failed for legacy panel {}", entry.title, ex);
                javax.swing.JOptionPane.showMessageDialog(bar, ex.getMessage(),
                        i18n.getString("error"), javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        });
        bar.add(save, BorderLayout.EAST);
        return bar;
    }

    private ActionResult invokeAction(String panelId, String actionId, Map<String, Object> values)
    {
        try
        {
            return api.invokeAction(panelId, actionId, values);
        }
        catch (Exception e)
        {
            LOGGER.error("Action {} on panel {} failed", actionId, panelId, e);
            return ActionResult.fail(e.getMessage() == null ? "Action failed" : e.getMessage());
        }
    }

    private DefaultMutableTreeNode childNamed(DefaultMutableTreeNode parent, String name)
    {
        for (int i = 0; i < parent.getChildCount(); i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (name.equals(child.getUserObject())) return child;
        }
        DefaultMutableTreeNode created = new DefaultMutableTreeNode(name);
        parent.add(created);
        return created;
    }

    private DefaultMutableTreeNode findFirstLeaf(DefaultMutableTreeNode node)
    {
        if (node.getChildCount() == 0)
        {
            Object o = node.getUserObject();
            return (o instanceof PanelSummary || o instanceof LegacyEntry) ? node : null;
        }
        for (int i = 0; i < node.getChildCount(); i++)
        {
            DefaultMutableTreeNode found = findFirstLeaf((DefaultMutableTreeNode) node.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    /** A wrapped legacy {@link OptionPanel} ready to drop into the unified
     *  tree. {@code path} is the breadcrumb (without the leaf), {@code title}
     *  is the displayed leaf label. */
    private record LegacyEntry(OptionPanel panel, List<String> path, String title) {}
}

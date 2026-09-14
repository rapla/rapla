package org.rapla.plugin.externaleventimport.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.ActionWrapper;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.externaleventimport.ExternalEventImportMetadata;
import org.rapla.plugin.externaleventimport.ExternalEventImportResult;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.plugin.externaleventimport.ResultColumn;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportResources;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportSubmitCallback;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Generic table panel for the import wizard. Columns and labels come from
 * {@link ExternalEventImportMetadata}; row data is taken from {@link ImportItem}
 * column maps. Carries no domain-specific knowledge.
 */
class ExternalEventImportPanel extends RaplaComponent implements RaplaWidget<JComponent>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalEventImportPanel.class);
    private static final int SOURCE_ID_COLUMN = -1; // logical: source id is tracked in parallel array, not visible

    private final ClientFacade clientFacade;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final ExternalEventImportResources resources;
    private final CalendarModel model;
    private final ExternalEventImportSubmitCallback callback;
    private final boolean hideCreate;
    private final ExternalEventImportMetadata metadata;

    private JTable table;
    private JPanel contentPane;
    private RaplaAction createReservationAction;
    private JLabel dateContainerLabel;

    /** Source IDs, one per visible row, parallel to the table model. */
    private List<String> rowSourceIds = new ArrayList<>();

    /** Source IDs of already-imported items (generic flag from {@link ImportItem#isImported()}).
     *  Create is disabled when every selected item is in this set. */
    private final java.util.Set<String> importedSourceIds = new java.util.HashSet<>();

    /** One value-filter combo per {@code metadata.filterColumns} key (e.g. Studiengang/Semester/Kurs). */
    private final Map<String, JComboBox<String>> filterCombos = new LinkedHashMap<>();
    private boolean populatingFilters;

    public ExternalEventImportPanel(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, CalendarModel model,
            DialogUiFactoryInterface dialogUiFactory, ExternalEventImportResources resources, ExternalEventImportMetadata metadata,
            ExternalEventImportResult result, ExternalEventImportSubmitCallback callback, boolean hideCreate) throws RaplaInitializationException
    {
        super(facade.getRaplaFacade(), i18n, raplaLocale);
        this.clientFacade = facade;
        this.dialogUiFactory = dialogUiFactory;
        this.resources = resources;
        this.model = model;
        this.metadata = metadata;
        this.callback = callback;
        this.hideCreate = hideCreate;

        contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(BorderFactory.createEmptyBorder(11, 10, 17, 11));
        contentPane.add(createContent(), BorderLayout.CENTER);
        contentPane.add(createButtons(), BorderLayout.SOUTH);

        loadResultIntoTable(result);
        updateComponentStates();
    }

    private JPanel createContent()
    {
        table = createTable();
        JScrollPane scrollPane = new JScrollPane(table);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 11, 0));
        JPanel filterPanel = createFilterPanel();
        if (filterPanel != null) panel.add(filterPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /** A "no filter" combo entry per filter column — shown as the column label, like the old dialog. */
    private JPanel createFilterPanel()
    {
        List<String> filterColumns = metadata.getFilterColumns();
        if (filterColumns == null || filterColumns.isEmpty()) return null;
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createTitledBorder("Filter"));
        for (String colKey : filterColumns)
        {
            String label = labelForColumn(colKey);
            JComboBox<String> combo = new JComboBox<>();
            combo.addActionListener(e -> applyFilters());
            filterCombos.put(colKey, combo);
            p.add(new JLabel(label));
            p.add(combo);
        }
        return p;
    }

    private void populateFilterCombos(ExternalEventImportResult result)
    {
        populatingFilters = true;
        for (Map.Entry<String, JComboBox<String>> e : filterCombos.entrySet())
        {
            String colKey = e.getKey();
            TreeSet<String> values = new TreeSet<>();
            for (ImportItem item : result.getItems())
            {
                Object v = item.getColumns().get(colKey);
                if (v != null && !v.toString().isEmpty()) values.add(v.toString());
            }
            JComboBox<String> combo = e.getValue();
            combo.removeAllItems();
            combo.addItem(labelForColumn(colKey));   // first entry = "no filter"
            for (String v : values) combo.addItem(v);
            combo.setSelectedIndex(0);
        }
        populatingFilters = false;
    }

    @SuppressWarnings("unchecked")
    private void applyFilters()
    {
        if (populatingFilters) return;
        TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) table.getRowSorter();
        if (sorter == null) return;
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        for (Map.Entry<String, JComboBox<String>> e : filterCombos.entrySet())
        {
            int idx = e.getValue().getSelectedIndex();
            if (idx <= 0) continue;   // 0 = the "no filter" label entry
            String sel = (String) e.getValue().getSelectedItem();
            int col = columnIndex(e.getKey());
            if (col < 0 || sel == null) continue;
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(sel) + "$", col));
        }
        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private int columnIndex(String colKey)
    {
        List<ResultColumn> cols = metadata.getResultColumns();
        for (int i = 0; i < cols.size(); i++) if (cols.get(i).key().equals(colKey)) return i;
        return -1;
    }

    private String labelForColumn(String colKey)
    {
        for (ResultColumn c : metadata.getResultColumns()) if (c.key().equals(colKey)) return c.label();
        return colKey;
    }

    private JTable createTable()
    {
        JTable t = new JTable();
        t.setAutoCreateColumnsFromModel(true);
        t.setAutoCreateRowSorter(true);
        t.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateComponentStates();
        });
        return t;
    }

    private JPanel createButtons() throws RaplaInitializationException
    {
        JPanel result = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        result.add(dateContainerLabel = new JLabel());
        result.add(Box.createHorizontalGlue());

        if (metadata.isSupportsCsvImport())
        {
            JButton csvImport = new JButton(resources.getString("import.from.csv"));
            csvImport.addActionListener(e -> importFromFile());
            result.add(csvImport);
        }

        if (!hideCreate)
        {
            createReservationAction = new RaplaAction(clientFacade, getI18n(), getRaplaLocale())
            {
                @Override
                public void actionPerformed()
                {
                    callback.submit(getSelectedSourceItemIds());
                }
            };
            createReservationAction.putValue(Action.ACTION_COMMAND_KEY, "createReservation");
            createReservationAction.putValue(Action.NAME, resources.getString("create.reservations"));
            JButton createReservation = new JButton(new ActionWrapper(createReservationAction));
            result.add(Box.createHorizontalGlue());
            result.add(createReservation);
        }

        result.setBorder(BorderFactory.createEmptyBorder(0, 0, 17, 0));
        return result;
    }

    public List<String> getSelectedSourceItemIds()
    {
        int[] viewRows = table.getSelectedRows();
        List<String> ids = new ArrayList<>();
        for (int viewRow : viewRows)
        {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < rowSourceIds.size())
            {
                ids.add(rowSourceIds.get(modelRow));
            }
        }
        return ids;
    }

    private void loadResultIntoTable(ExternalEventImportResult result)
    {
        List<ResultColumn> columns = metadata.getResultColumns();
        String[] headers = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) headers[i] = columns.get(i).label();

        Object[][] data = new Object[result.getItems().size()][columns.size()];
        rowSourceIds = new ArrayList<>(result.getItems().size());
        int r = 0;
        importedSourceIds.clear();
        for (ImportItem item : result.getItems())
        {
            rowSourceIds.add(item.getSourceItemId());
            if (item.isImported()) importedSourceIds.add(item.getSourceItemId());
            for (int c = 0; c < columns.size(); c++)
            {
                Object v = item.getColumns().get(columns.get(c).key());
                data[r][c] = v;
            }
            r++;
        }
        table.setModel(new DefaultTableModel(data, headers));
        table.setColumnModel(new XTableColumnModel());
        table.createDefaultColumnsFromModel();
        populateFilterCombos(result);
    }

    private void importFromFile()
    {
        // CSV upload — wizard delegates to server via service.uploadCsv. The controller is
        // not available here, so trigger a callback. For now this is a stub: CSV upload
        // will be wired through ExternalEventImportController in a follow-up.
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setAcceptAllFileFilterUsed(true);
        String filterLabel = metadata.getCsvFileFilterLabel();
        if (filterLabel == null || filterLabel.isEmpty()) filterLabel = resources.getString("csv.files");
        fc.setFileFilter(new FileNameExtensionFilter(filterLabel, "csv"));

        fc.showOpenDialog(contentPane);
        File selFile = fc.getSelectedFile();
        if (selFile != null && selFile.exists() && selFile.canRead())
        {
            DialogInterface info = dialogUiFactory.createInfoDialog(new SwingPopupContext(contentPane, null), "CSV import",
                    "CSV upload not yet wired through the controller — file: " + selFile.getName());
            info.start(false).exceptionally(e -> LOGGER.error(e.getMessage(), e));
        }
    }

    private void updateComponentStates()
    {
        List<String> selected = getSelectedSourceItemIds();
        // Generic rule: create is enabled only when the selection contains at least one
        // not-yet-imported item — selecting only already-imported items would create duplicates.
        boolean valid = callback.isValidSelection(selected) && hasUnimportedSelected(selected);
        if (createReservationAction != null) createReservationAction.setEnabled(valid);
        updateTimeIntervalLabel();
    }

    private boolean hasUnimportedSelected(List<String> selected)
    {
        for (String id : selected)
        {
            if (!importedSourceIds.contains(id)) return true;
        }
        return false;
    }

    private void updateTimeIntervalLabel()
    {
        SwingUtilities.invokeLater(() -> {
            java.time.LocalDateTime start = model.getStartDate();
            java.time.LocalDateTime end = model.getEndDate();
            RaplaLocale loc = getRaplaLocale();
            String startStr = start == null ? "" : loc.formatDate(start) + ", " + loc.formatTime(start);
            String endStr = end == null ? "" : loc.formatDate(end) + ", " + loc.formatTime(end);
            dateContainerLabel.setText(resources.getString("selected.interval") + ": " + startStr + " - " + endStr);
        });
    }

    @Override
    public JComponent getComponent()
    {
        return contentPane;
    }
}

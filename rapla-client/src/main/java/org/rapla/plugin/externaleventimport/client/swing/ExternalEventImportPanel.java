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
import org.rapla.logger.Logger;
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
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Generic table panel for the import wizard. Columns and labels come from
 * {@link ExternalEventImportMetadata}; row data is taken from {@link ImportItem}
 * column maps. Carries no domain-specific knowledge.
 */
class ExternalEventImportPanel extends RaplaComponent implements RaplaWidget<JComponent>
{
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

    public ExternalEventImportPanel(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model,
            DialogUiFactoryInterface dialogUiFactory, ExternalEventImportResources resources, ExternalEventImportMetadata metadata,
            ExternalEventImportResult result, ExternalEventImportSubmitCallback callback, boolean hideCreate) throws RaplaInitializationException
    {
        super(facade.getRaplaFacade(), i18n, raplaLocale, logger);
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
        panel.add(scrollPane);
        return panel;
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
            createReservationAction = new RaplaAction(clientFacade, getI18n(), getRaplaLocale(), getLogger())
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
        for (ImportItem item : result.getItems())
        {
            rowSourceIds.add(item.getSourceItemId());
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
            info.start(false).exceptionally(e -> getLogger().error(e.getMessage(), e));
        }
    }

    private void updateComponentStates()
    {
        boolean valid = callback.isValidSelection(getSelectedSourceItemIds());
        if (createReservationAction != null) createReservationAction.setEnabled(valid);
        updateTimeIntervalLabel();
    }

    private void updateTimeIntervalLabel()
    {
        SwingUtilities.invokeLater(() -> {
            java.time.LocalDateTime start = model.getStartDate();
            java.time.LocalDateTime end = model.getEndDate();
            String startStr = start == null ? "" : start.toString();
            String endStr = end == null ? "" : end.toString();
            dateContainerLabel.setText(startStr + " — " + endStr);
        });
    }

    @Override
    public JComponent getComponent()
    {
        return contentPane;
    }
}

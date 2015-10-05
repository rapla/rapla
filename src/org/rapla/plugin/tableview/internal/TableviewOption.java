package org.rapla.plugin.tableview.internal;

import java.awt.BorderLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.gui.internal.edit.fields.MultiLanguageField;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

/**
 *
 */
public class TableviewOption extends DefaultPluginOption {

    private final JPanel list = new JPanel();
    private final List<TableColumnConfig> tablerows = new ArrayList<TableColumnConfig>();
    private final List<TableRow> rows = new ArrayList<TableRow>();
    private JPanel main;
    private TableConfig tableConfig;
    private JComboBox typeSelection;
    private Sorting sorting;

    public TableviewOption(RaplaContext sm) {
        super(sm);
    }

    @Override
    protected JPanel createPanel() throws RaplaException {
        main = new JPanel();
        LayoutManager layout = new BoxLayout(main, BoxLayout.Y_AXIS);
        main.setLayout(layout);
        {        // Row definitions
            final JScrollPane tableOfAllRows = new JScrollPane(list);
            final JPanel containerForTableOfAllRows = new JPanel();
            containerForTableOfAllRows.setLayout(new BorderLayout());
            containerForTableOfAllRows.add(tableOfAllRows, BorderLayout.CENTER);
            final JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(new JLabel("Definition of each column for the tables:"), BorderLayout.NORTH);
            wrapper.add(containerForTableOfAllRows, BorderLayout.CENTER);
            main.add(wrapper);
        }
        {
            // spacer
            main.add(new JPanel(new BorderLayout(1, 0)));
        }
        {// Ordering definitionr
            JPanel containerForOrderingAndValueDefinition = new JPanel(new BorderLayout());
            typeSelection = new JComboBox();
            typeSelection.addItem(new TypeSelection("events", getString("reservations")));
            typeSelection.addItem(new TypeSelection("appointments", getString("appointments")));
            typeSelection.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    final TypeSelection selectedType = (TypeSelection) typeSelection.getSelectedItem();
                    if (selectedType != null) {
                        sorting.init(selectedType.key);
                    }
                }
            });
            containerForOrderingAndValueDefinition.add(typeSelection, BorderLayout.NORTH);
            sorting = new Sorting();
            containerForOrderingAndValueDefinition.add(sorting, BorderLayout.CENTER);
            final JPanel wrapperOrdering = new JPanel(new BorderLayout());
            final JPanel deviderPanel = new JPanel(new BorderLayout());
            wrapperOrdering.add(deviderPanel, BorderLayout.NORTH);
            wrapperOrdering.add(containerForOrderingAndValueDefinition, BorderLayout.CENTER);
            deviderPanel.add(new JLabel("Definition of column ordering for each table:"));
            main.add(wrapperOrdering);
        }
        return main;
    }

    protected void initRows() {
        rows.clear();
        for (TableColumnConfig slot : tablerows) {
            TableRow row = new TableRow(slot, getContext());
            rows.add(row);
        }
    }

    protected void update() {
        tablerows.clear();
        tablerows.addAll(mapToRows());
        list.removeAll();
        TableLayout tableLayout = new TableLayout();
        list.setLayout(tableLayout);
        tableLayout.insertColumn(0, TableLayout.PREFERRED);
        tableLayout.insertColumn(1, 10);
        tableLayout.insertColumn(2, TableLayout.FILL);
        list.setLayout(tableLayout);

        tableLayout.insertRow(0, TableLayout.PREFERRED);
        list.add(new JLabel("Row Name"), "0,0");
        list.add(new JLabel("Value Type"), "2,0");
        int i = 0;
        for (TableRow row : rows) {
            tableLayout.insertRow(++i, TableLayout.MINIMUM);
            list.add(row.nameField.getComponent(), "0," + i);
            list.add(row.typeField, "2," + i);
        }
        list.validate();
        list.repaint();
        main.validate();
        main.repaint();
        sorting.init(((TypeSelection) typeSelection.getSelectedItem()).key);
    }

    protected void addChildren(DefaultConfiguration newConfig) {
        final RaplaConfiguration raplaConfig = TableConfig.print(tableConfig);
        newConfig.add(raplaConfig);
    }

    protected List<TableColumnConfig> mapToRows() {
        List<TableColumnConfig> newRows = new ArrayList<TableColumnConfig>();

        for (TableColumnConfig column : tableConfig.getAllColumns()) {
            newRows.add(column);
        }
        return newRows;
    }

    @Override
    protected Configuration getConfig() throws RaplaException {
        Configuration config = preferences.getEntry(TableViewPlugin.CONFIG, null);
        if (config == null) {
            config = TableConfig.print(TableConfig.getDefaultConfig());
        }
        return config;
    }

    protected void readConfig(Configuration config) {
        try {
            this.tableConfig = TableConfig.read((RaplaConfiguration) config);
            tablerows.clear();
            tablerows.addAll(this.tableConfig.getAllColumns());
            initRows();
            update();
        } catch (ConfigurationException ex) {
            getLogger().error("Error loading configuration for tableview plugin: " + ex.getMessage(), ex);
        }
    }

    public void show() throws RaplaException {
        super.show();
    }

    public void commit() throws RaplaException {
        tablerows.clear();
        writePluginConfig(false);
        TypedComponentRole<RaplaConfiguration> configEntry = TableViewPlugin.CONFIG;
        final RaplaConfiguration newConfig = TableConfig.print(tableConfig);
        preferences.putEntry(configEntry, newConfig);
    }

    public Class<? extends PluginDescriptor<?>> getPluginClass() {
        return TableViewPlugin.class;
    }

    public String getName(Locale locale) {
        return "Tableview Plugin";
    }

    private class TableRow {

        private final JTextField typeField = new JTextField();
        private final MultiLanguageField nameField;

        private TableRow(TableColumnConfig slot, RaplaContext context) {
            nameField = new MultiLanguageField(context);
            typeField.setText(slot.getType());
            typeField.setEditable(false);
            nameField.setValue(slot.getName());
        }
    }

    private static class TypeSelection {

        private final String key;
        private final String name;

        public TypeSelection(String key, String name) {
            this.key = key;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

    }

    private static class SortingRow extends JLabel {

        private final TableColumnConfig columnConfig;

        private SortingRow(TableColumnConfig column, Locale locale) {
            super(createName(column, locale));
            this.columnConfig = column;
        }

        private static String createName(TableColumnConfig column, Locale locale) {
            final String name = column.getName().getName(locale.getLanguage());
            return name != null && !name.isEmpty() ? name : " ";
        }

        @Override
        public String toString() {
            return getText();
        }

    }

    private class Sorting extends JPanel {

        private final RaplaArrowButton moveUpButton = new RaplaArrowButton('^', 25);
        private final RaplaArrowButton moveDownButton = new RaplaArrowButton('v', 25);
        private final DefaultListModel listModel = new DefaultListModel();
        private final JList list = new JList(listModel);
        private String selectedTable = null;
        private final List<SortingRow> rows = new ArrayList<>();
        private final JComboBox<SortingRow> allSortingRows = new JComboBox<SortingRow>();

        public Sorting() {
            super();
            final BorderLayout layout = new BorderLayout();
            setLayout(layout);
            final JPanel header = new JPanel();
            header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
            add(header, BorderLayout.NORTH);
            header.add(moveUpButton, BorderLayout.WEST);
            moveUpButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    sort(true);
                }
            });
            header.add(moveDownButton, BorderLayout.EAST);
            moveDownButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    sort(false);
                }
            });
            header.add(allSortingRows);
            final JButton addButton = new JButton(getString("insert"));
            addButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    final SortingRow selectedItem = (SortingRow) allSortingRows.getSelectedItem();
                    if (selectedItem != null) {
                        tableConfig.addView(selectedTable, selectedItem.columnConfig);
                        init(selectedTable);
                        list.setSelectedIndex(listModel.getSize() - 1);
                    }
                }
            });
            header.add(addButton);
            final JButton deleteButton = new JButton(getString("delete"));
            deleteButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    final int[] selectedIndices = list.getSelectedIndices();
                    if (selectedIndices != null) {
                        for (int i = selectedIndices.length - 1; i >= 0; i--) {
                            int index = selectedIndices[i];
                            listModel.remove(index);
                        }
                        tableConfig.removeView(selectedTable);
                        for(int i = 0; i < listModel.size(); i++)
                        {
                            final SortingRow row = (SortingRow) listModel.get(i);
                            tableConfig.addView(selectedTable, row.columnConfig);
                        }
                        list.validate();
                        list.repaint();
                    }
                }
            });
            header.add(deleteButton);
            final JScrollPane tableOfAllRows = new JScrollPane(list);
            final JPanel containerForAllRows = new JPanel();
            containerForAllRows.setLayout(new BorderLayout());
            containerForAllRows.add(tableOfAllRows, BorderLayout.CENTER);
            add(containerForAllRows, BorderLayout.CENTER);
        }

        private void init(String selectedTable) {
            this.selectedTable = selectedTable;
            final Collection<TableColumnConfig> columns = tableConfig.getColumns(selectedTable);
            final Locale locale = getLocale();
            listModel.removeAllElements();
            rows.clear();
            if (columns != null) {
                for (TableColumnConfig column : columns) {
                    final SortingRow newRow = new SortingRow(column, locale);
                    listModel.addElement(newRow);
                    rows.add(newRow);
                }
            }
            allSortingRows.removeAllItems();
            final Set<TableColumnConfig> allColumns = tableConfig.getAllColumns();
            for (TableColumnConfig column : allColumns) {
                allSortingRows.addItem(new SortingRow(column, locale));
            }
            allSortingRows.validate();
            allSortingRows.repaint();
            list.validate();
            list.repaint();
            validate();
            repaint();
        }

        private void sort(boolean moveUpwards) {
            if (rows.size() > 0) {
                // sort
                final int[] selectedIndices = list.getSelectedIndices();
                if (selectedIndices != null) {
                    if (moveUpwards) {
                        for (int i = 0; i < selectedIndices.length; i++) {
                            final int indexToPullUp = selectedIndices[i];
                            if (indexToPullUp > 0) {
                                final SortingRow row = rows.remove(indexToPullUp);
                                rows.add(Math.max(0, indexToPullUp - 1), row);
                            }
                        }
                    } else {
                        for (int i = selectedIndices.length - 1; i >= 0; i--) {
                            final int indexToPullDown = selectedIndices[i];
                            if (indexToPullDown < list.getModel().getSize()) {
                                final SortingRow row = rows.remove(indexToPullDown);
                                final int newIndex = Math.max(0, Math.min(rows.size(), indexToPullDown + 1));
                                rows.add(newIndex, row);
                            }
                        }
                    }
                    // remove old
                    tableConfig.removeView(selectedTable);
                    // write again
                    for (SortingRow row : rows) {
                        tableConfig.addView(selectedTable, row.columnConfig);
                    }
                    init(selectedTable);
                    // select the new ones
                    final int[] newSelectedIndices = new int[selectedIndices.length];
                    for (int i = 0; i < selectedIndices.length; i++) {
                        int selectedIndice = selectedIndices[i];
                        int newSelectedIndex = Math.max(0, Math.min(listModel.size() - 1, selectedIndice + (moveUpwards ? -1 : +1)));
                        newSelectedIndices[i] = newSelectedIndex;
                    }
                    list.setSelectedIndices(newSelectedIndices);
                }
            }
        }
    }
}

package org.rapla.plugin.tableview.internal;

import java.awt.BorderLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.swing.internal.edit.fields.MultiLanguageField;
import org.rapla.client.swing.internal.edit.fields.MultiLanguageField.MultiLanguageFieldFactory;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;
import org.rapla.plugin.tableview.internal.TableConfig.ViewDefinition;

/**
 *
 */
@Extension(provides = PluginOptionPanel.class, id = TableViewPlugin.PLUGIN_ID) public class TableviewOption implements PluginOptionPanel, ChangeListener
{
    private final JPanel list = new JPanel();
    private final List<TableColumnConfig> tablerows = new ArrayList<TableColumnConfig>();
    private final List<TableRow> rows = new ArrayList<TableRow>();
    private JPanel main;
    private TableConfig tableConfig;
    private JComboBox typeSelection;
    private Sorting sorting;

    private final RaplaResources i18n;
    private final Logger logger;
    private final RaplaLocale raplaLocale;
    private Preferences preferences;
    private final TableConfig.TableConfigLoader tableConfigLoader;
    private final MultiLanguageFieldFactory multiLanguageFieldFactory;

    @Inject
    public TableviewOption(RaplaFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TableConfig.TableConfigLoader tableConfigLoader,
            MultiLanguageFieldFactory multiLanguageFieldFactory)
    {
        this.i18n = i18n;
        this.logger = logger;
        this.raplaLocale = raplaLocale;
        this.tableConfigLoader = tableConfigLoader;
        this.multiLanguageFieldFactory = multiLanguageFieldFactory;
    }

    public void setPreferences(Preferences preferences)
    {
        this.preferences = preferences;
    }

    /** commits the changes in the option Dialog.*/
    public void commit() throws RaplaException
    {
        tablerows.clear();
        TypedComponentRole<RaplaConfiguration> configEntry = TableViewPlugin.CONFIG;
        final RaplaConfiguration newConfig = TableConfig.print(tableConfig);
        preferences.putEntry(configEntry, newConfig);
    }

    /** called when the option Panel is selected for displaying.*/
    public void show() throws RaplaException
    {
        createPanel();
        this.tableConfig = tableConfigLoader.read(preferences, true);
        tablerows.clear();
        tablerows.addAll(this.tableConfig.getAllColumns());
        initRows();
    }

    private void createPanel()
    {
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
            typeSelection.addActionListener(new ActionListener()
            {
                @Override public void actionPerformed(ActionEvent e)
                {
                    final TypeSelection selectedType = (TypeSelection) typeSelection.getSelectedItem();
                    if (selectedType != null)
                    {
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
    }

    public JComponent getComponent()
    {
        return main;
    }

    private boolean updateting = false;
    protected void initRows()
    {
        try
        {
            updateting = true;
            for (TableRow row : rows)
            {
                row.nameField.removeChangeListener(this);
            }
            rows.clear();
            for (TableColumnConfig slot : tablerows)
            {
                TableRow row = new TableRow(slot, multiLanguageFieldFactory);
                rows.add(row);
                row.nameField.addChangeListener(this);
            }
        }
        finally
        {
            updateting = false;
        }
    }

    protected void update()
    {
        try
        {
            updateting = true;


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
            for (TableRow row : rows)
            {
                tableLayout.insertRow(++i, TableLayout.MINIMUM);
                list.add(row.nameField.getComponent(), "0," + i);
                list.add(row.typeField, "2," + i);
            }
            list.validate();
            list.repaint();
            main.validate();
            main.repaint();
            updateTableMap();
        }
        finally
        {
            updateting = false;
        }
    }

    private void updateTableMap()
    {
        tablerows.clear();
        tablerows.addAll(mapToRows());
        typeSelection.removeAllItems();
        for (Entry<String, ViewDefinition> entry : tableConfig.getViewMap().entrySet())
        {
            final String key = entry.getKey();
            final ViewDefinition value = entry.getValue();
            final String name = value.getName(raplaLocale.getLocale());
            typeSelection.addItem(new TypeSelection(key, name));
        }
        sorting.init(((TypeSelection) typeSelection.getSelectedItem()).key);
    }

    protected void addChildren(DefaultConfiguration newConfig)
    {
        final RaplaConfiguration raplaConfig = TableConfig.print(tableConfig);
        newConfig.add(raplaConfig);
    }

    protected List<TableColumnConfig> mapToRows()
    {
        List<TableColumnConfig> newRows = new ArrayList<TableColumnConfig>();

        for (TableColumnConfig column : tableConfig.getAllColumns())
        {
            newRows.add(column);
        }
        return newRows;
    }

    public String getName(Locale locale)
    {
        return "Tableview Plugin";

    }

    static private class TableRow
    {

        private final JTextField typeField = new JTextField();
        private final MultiLanguageField nameField;

        private TableRow(TableColumnConfig slot, MultiLanguageFieldFactory multiLanguageFieldFactory)
        {
            nameField = multiLanguageFieldFactory.create();
            typeField.setText(slot.getType());
            typeField.setEditable(false);
            nameField.setValue(slot.getName());
        }

    }

    private static class TypeSelection
    {

        private final String key;
        private final String name;

        public TypeSelection(String key, String name)
        {
            this.key = key;
            this.name = name;
        }

        @Override public String toString()
        {
            return name;
        }

    }


    private static class SortingRow extends JLabel
    {

        private final TableColumnConfig columnConfig;

        private SortingRow(TableColumnConfig column, Locale locale)
        {
            super(column.getName().getName(locale.getLanguage()));
            this.columnConfig = column;
        }

        @Override public String toString()
        {
            return getText();
        }

    }

    private class Sorting extends JPanel
    {

        private final RaplaArrowButton moveUpButton = new RaplaArrowButton('^', 25);
        private final RaplaArrowButton moveDownButton = new RaplaArrowButton('v', 25);
        private final DefaultListModel listModel = new DefaultListModel();
        private final JList list = new JList(listModel);
        private String selectedTable = null;
        private final List<SortingRow> rows = new ArrayList<SortingRow>();
        private final JComboBox allSortingRows = new JComboBox();

        public Sorting()
        {
            super();
            //            final GridLayout layout = new GridLayout(2, 1);
            final BorderLayout layout = new BorderLayout();
            setLayout(layout);
            final JPanel header = new JPanel();
            header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
            add(header, BorderLayout.NORTH);
            header.add(moveUpButton, BorderLayout.WEST);
            moveUpButton.addActionListener(new ActionListener()
            {
                @Override public void actionPerformed(ActionEvent e)
                {
                    sort(true);
                }
            });
            header.add(moveDownButton, BorderLayout.EAST);
            moveDownButton.addActionListener(new ActionListener()
            {
                @Override public void actionPerformed(ActionEvent e)
                {
                    sort(false);
                }
            });
            header.add(allSortingRows);
            final JButton addButton = new JButton(i18n.getString("insert"));
            addButton.addActionListener(new ActionListener()
            {
                @Override public void actionPerformed(ActionEvent e)
                {
                    final SortingRow selectedItem = (SortingRow) allSortingRows.getSelectedItem();
                    if (selectedItem != null)
                    {
                        tableConfig.getOrCreateView(selectedTable).addColumn(selectedItem.columnConfig);
                        init(selectedTable);
                        list.setSelectedIndex(listModel.getSize() - 1);
                    }
                }
            });
            header.add(addButton);
            final JButton deleteButton = new JButton(i18n.getString("delete"));
            deleteButton.addActionListener(new ActionListener()
            {
                @Override public void actionPerformed(ActionEvent e)
                {
                    final int[] selectedIndices = list.getSelectedIndices();
                    if (selectedIndices != null)
                    {
                        List<TableColumnConfig> toRemove = new ArrayList<TableColumnConfig>();
                        for (int i = selectedIndices.length - 1; i >= 0; i--)
                        {
                            int index = selectedIndices[i];
                            final SortingRow row = (SortingRow) listModel.remove(index);
                            toRemove.add(row.columnConfig);
                        }
                        final ViewDefinition view = tableConfig.getOrCreateView(selectedTable);
                        for (TableColumnConfig row : toRemove)
                        {
                            view.removeColumn(row);
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

        private void init(String selectedTable)
        {
            this.selectedTable = selectedTable;
            final Collection<TableColumnConfig> columns = tableConfig.getColumns(selectedTable);
            final Locale locale = getLocale();
            listModel.removeAllElements();
            rows.clear();
            if (columns != null)
            {
                for (TableColumnConfig column : columns)
                {
                    final SortingRow newRow = new SortingRow(column, locale);
                    listModel.addElement(newRow);
                    rows.add(newRow);
                }
            }
            allSortingRows.removeAllItems();
            final Set<TableColumnConfig> allColumns = tableConfig.getAllColumns();
            for (TableColumnConfig column : allColumns)
            {
                allSortingRows.addItem(new SortingRow(column, locale));
            }
            allSortingRows.validate();
            allSortingRows.repaint();
            list.validate();
            list.repaint();
            validate();
            repaint();
        }

        private void sort(boolean moveUpwards)
        {
            if (rows.size() > 0)
            {
                // sort
                final int[] selectedIndices = list.getSelectedIndices();
                if (selectedIndices != null)
                {
                    if (moveUpwards)
                    {
                        for (int i = 0; i < selectedIndices.length; i++)
                        {
                            final int indexToPullUp = selectedIndices[i];
                            if (indexToPullUp > 0)
                            {
                                final SortingRow row = rows.remove(indexToPullUp);
                                final int index = Math.max(0, indexToPullUp - 1);
                                rows.add(index, row);
                            }
                        }
                    }
                    else
                    {
                        for (int i = selectedIndices.length - 1; i >= 0; i--)
                        {
                            final int indexToPullDown = selectedIndices[i];
                            if (indexToPullDown < list.getModel().getSize())
                            {
                                final SortingRow row = rows.remove(indexToPullDown);
                                final int newIndex = Math.max(0, Math.min(rows.size(), indexToPullDown + 1));
                                rows.add(newIndex, row);
                            }
                        }
                    }
                    final ViewDefinition view = tableConfig.getOrCreateView(selectedTable);
                    final Collection<TableColumnConfig> columns = tableConfig.getColumns(selectedTable);
                    for (TableColumnConfig columnConfig : columns)
                    {
                        view.removeColumn(columnConfig);
                    }
                    // write again
                    for (SortingRow row : rows)
                    {
                        view.addColumn(row.columnConfig);
                    }
                    init(selectedTable);
                    // select the new ones
                    final int[] newSelectedIndices = new int[selectedIndices.length];
                    for (int i = 0; i < selectedIndices.length; i++)
                    {
                        int selectedIndice = selectedIndices[i];
                        int newSelectedIndex = Math.max(0, Math.min(listModel.size() - 1, selectedIndice + (moveUpwards ? -1 : +1)));
                        newSelectedIndices[i] = newSelectedIndex;
                    }
                    list.setSelectedIndices(newSelectedIndices);
                }
            }

        }

    }

    @Override
    public void stateChanged(ChangeEvent e)
    {
        if ( !updateting)
        {
            updateTableMap();
        }
    }
}

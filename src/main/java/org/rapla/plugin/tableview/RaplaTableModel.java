package org.rapla.plugin.tableview;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import org.jetbrains.annotations.NotNull;
import org.rapla.facade.CalendarModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsType
public class RaplaTableModel<T, C>
{
    private List<T> rows = new ArrayList<>();
    private RaplaTableColumn<T, C>[] columns;
    private List<Object[]> data = new ArrayList<>();
    private List<String> columnNames = new ArrayList<>();

    public RaplaTableModel( Collection<RaplaTableColumn<T, C>> columnPlugins) {
        columns = new RaplaTableColumn[columnPlugins.size()];
        int column = 0;
        for (RaplaTableColumn<T, C> col: columnPlugins)
        {
        	columnNames.add( col.getColumnName());
        	this.columns[column++]= col;
        }
    }

    @NotNull
    @JsIgnore
    static public Map<RaplaTableColumn, Integer> getSortDirections(CalendarModel model,List<? extends RaplaTableColumn> columPlugins, String tableViewName)
    {
        final String sortingStringOption = TableViewPlugin.getSortingStringOption(tableViewName);
        String sorting = model.getOption(sortingStringOption);
        Map<RaplaTableColumn, Integer> sortDirections = new LinkedHashMap<>();
        if (sorting != null)
        {
            for (String string : sorting.split(";"))
            {
                int length = string.length();
                int column = Integer.parseInt(string.substring(0, length - 1));
                char order = string.charAt(length - 1);
                if (columPlugins.size() > column)
                {
                    RaplaTableColumn columPlugin = columPlugins.get(column);
                    int sortDirection = order == '+' ? 1 : -1;
                    sortDirections.put(columPlugin, sortDirection);
                }
            }
        }
        if (sortDirections.isEmpty())
        {
            sortDirections.put(columPlugins.get(0), 1);
        }
        return sortDirections;
    }

    public RaplaTableColumn[] getColumns()
    {
        return columns;
    }

    public List<String> getColumnNames()
    {
        return columnNames;
    }

    public RaplaTableModel<T,C> setObjects(List<T> objects)
    {
        data = objects.stream().parallel()
                .map((obj) -> Arrays.stream(columns).map((column) -> column.getValue(obj)).toArray()).collect(Collectors.toList());
        this.rows = objects;
        return this;
    }

    public T getObjectAt(int row) {
        return this.rows.get(row);
    }

    public int getColumnCount() {
        return columns.length;
    }
    
    public Object[][] getAllRows() {
        if (data == null) {
            return new Object[0][0];
        }
        return data.toArray(new Object[data.size()][columns.length]);
    }
    
    public int getRowCount() {
        if ( data != null)
            return data.size();
        else
            return 0;
    }


    public Object getValueAt( int rowIndex, int columnIndex )
    {
        final Object[] rowData = data.get(rowIndex);
        final Object o = rowData[columnIndex];
        return o;
    }
    
    public Object[] getRowData( int rowIndex)
    {
        return data.get(rowIndex);
    }
    
    public Class<?> getColumnClass(int columnIndex) {
        RaplaTableColumn<T, C> tableColumn = columns[ columnIndex];
        return tableColumn.getColumnClass();
    }

    @Override
    public String toString()
    {
        return data.toString();
    }
}

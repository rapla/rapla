package org.rapla.plugin.tableview;

import org.jetbrains.annotations.NotNull;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarModel;

import java.util.*;
import java.util.stream.Collectors;

public class RaplaTableModel<T>
{
    private List<T> rows = new ArrayList<>();
    private final RaplaTableColumn<T>[] columns;
    private List<Object[]> data = new ArrayList<>();
    private final List<String> columnNames = new ArrayList<>();

    public RaplaTableModel( Collection<RaplaTableColumn<T>> columnPlugins) {
        columns = new RaplaTableColumn[columnPlugins.size()];
        int column = 0;
        for (RaplaTableColumn<T> col: columnPlugins)
        {
        	columnNames.add( col.getColumnName());
        	this.columns[column++]= col;
        }
    }

    @NotNull
    static public <T> Map<RaplaTableColumn<T>, Integer> getSortDirections(CalendarModel model,List<? extends RaplaTableColumn<T>> columPlugins, String tableViewName)
    {
        final String sortingStringOption = TableViewPlugin.getSortingStringOption(tableViewName);
        String sorting = model.getOption(sortingStringOption);
        Map<RaplaTableColumn<T>, Integer> sortDirections = new LinkedHashMap<>();
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
        //if (sortDirections.isEmpty())
        //{
        //    sortDirections.put(columPlugins.get(0), 1);
        //}
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

    public RaplaTableModel<T> setObjects(List<T> objects)
    {
        String contextAnnotationName = DynamicTypeAnnotations.KEY_NAME_FORMAT;
        data = objects.stream().parallel()
                .map((obj) -> Arrays.stream(columns).map((column) -> column.getValue(obj, contextAnnotationName)).toArray()).collect(Collectors.toList());
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
        RaplaTableColumn<T> tableColumn = columns[ columnIndex];
        return tableColumn.getColumnClass();
    }

    @Override
    public String toString()
    {
        return data.toString();
    }

    private static final String LINE_BREAK = "\n";
    private static final String CELL_BREAK = ";";
    static public <T> String getCSV(List<RaplaTableColumn<T>> columns, List<T> rows, String contextAnnotationName, boolean addIds)
    {
        StringBuffer buf = new StringBuffer();
        if ( addIds ) {
            buf.append("id");
            buf.append(CELL_BREAK);
        }
        for (RaplaTableColumn column : columns)
        {
            buf.append(column.getColumnName());
            buf.append(CELL_BREAK);
        }
        for (T row : rows)
        {
            buf.append(LINE_BREAK);
            T rowObject = row;
            if ( addIds ) {
                if (rowObject instanceof Entity)
                    buf.append(((Entity) rowObject).getId());
                buf.append(CELL_BREAK);
            }
            for (RaplaTableColumn column : columns)
            {
                Object value = column.getValue(rowObject, contextAnnotationName);
                Class columnClass = column.getColumnClass();
                boolean isDate = columnClass.equals(java.util.Date.class);
                String formated = "";
                if (value != null)
                {
                    if (isDate)
                    {
                        String timestamp = DateTools.formatDateTime( (java.util.Date) value);
                        formated = timestamp;
                    }
                    else
                    {
                        String escaped = escape(value);
                        formated = escaped;
                    }
                }
                buf.append(formated);
                buf.append(CELL_BREAK);
            }
        }
        final String result = buf.toString();
        return result;
    }


    static private String escape(Object cell) {
        return cell.toString().replace(LINE_BREAK, " ").replace(CELL_BREAK, " ");
    }

    static public <T> List<T> sortRows(Collection<T> rowObjects, Map<RaplaTableColumn<T>, Integer> sortDirections,Comparator<T> fallbackComparator, String  contextAnnotationName) {
        List<T> result = new ArrayList<>(rowObjects);
        Comparator<T> comparator = new Comparator<T>() {
            public int compare(T o1, T o2) {
                if (o2.equals(o1)) {
                    return 0;
                }
                for (Map.Entry<RaplaTableColumn<T>, Integer> entry : sortDirections.entrySet()) {
                    RaplaTableColumn column = entry.getKey();
                    int direction = entry.getValue();
                    Object v1 = column.getValue(o1, contextAnnotationName);
                    Object v2 = column.getValue(o2, contextAnnotationName);
                    if (v1 != null && v2 != null) {
                        Class<?> columnClass = column.getColumnClass();
                        if (columnClass.equals(String.class)) {
                            return String.CASE_INSENSITIVE_ORDER.compare(v1.toString(), v2.toString()) * direction;
                        } else if (v1 instanceof Comparable) {
                            return ((Comparable) v1).compareTo(v2) * direction;
                        }
                    }
                }
                return fallbackComparator.compare(o1, o2);
            }
        };
        Collections.sort(result, comparator);
        return  result;
    }

}

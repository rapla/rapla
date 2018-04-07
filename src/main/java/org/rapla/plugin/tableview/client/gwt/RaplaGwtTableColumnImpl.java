package org.rapla.plugin.tableview.client.gwt;

import jsinterop.annotations.JsType;
import org.rapla.entities.User;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.TableColumnType;
import org.rapla.plugin.tableview.internal.AbstractRaplaTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

@JsType
public class RaplaGwtTableColumnImpl<T> extends AbstractRaplaTableColumn<T, Object>
{
    public RaplaGwtTableColumnImpl(TableColumnConfig column, RaplaLocale raplaLocale, ClientFacade facade, User user)
    {
        super(column, raplaLocale, facade.getRaplaFacade(),user);
    }

    @Override
    public void init(Object column)
    {
    }
    
    @Override
    public String getColumnName() {
        return super.getColumnName();
    }
    
    @Override
    public TableColumnType getType() {
        return super.getType();
    }
    
    @Override
    public Object getValue(final T object) {
        return super.getValue(object);
    }
    
    @Override
    public String getHtmlValue(final T object) {
        return super.getHtmlValue(object);
    }
    
    @Override
    protected Object format(final Object object) {
        return super.format(object);
    }
    
    @Override
    protected String getAnnotationName() {
        return super.getAnnotationName();
    }
    
    @Override
    public Class<?> getColumnClass() {
        return super.getColumnClass();
    }
    
    @Override
    protected boolean isDatetime() {
        return super.isDatetime();
    }
    
    @Override
    protected boolean isDate() {
        return super.isDate();
    }
    
    @Override
    protected String formatHtml(final Object value) {
        return super.formatHtml(value);
    }
}
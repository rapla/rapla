package org.rapla.plugin.eventtimecalculator;

import javax.swing.table.TableColumn;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

/**
* User: kuestermann
* Date: 22.08.12
* Time: 09:30
*/
public class DurationColumn extends RaplaComponent implements ModificationListener {

    public DurationColumn(RaplaContext context)
    {
        super(context);
        getUpdateModule().addModificationListener( this);
    }


    public void init(TableColumn column) {
        column.setMaxWidth(90);
        column.setPreferredWidth(90);
    }


    public String getColumnName() {
        I18nBundle i18n = getService(EventTimeCalculatorPlugin.RESOURCE_FILE);
        return i18n.getString("duration");
    }


    public Class<?> getColumnClass() {
        return String.class;
    }

    protected boolean validConf = false;
	public void dataChanged(ModificationEvent evt) throws RaplaException {
		if ( evt.isModified(Preferences.TYPE))
		{
			validConf = false;
		}
	}


}

package org.rapla.plugin.tableview.internal;

import javax.inject.Inject;
import javax.swing.table.TableColumn;

import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.extensionpoints.AppointmentTableColumn;

@Extension(provides = AppointmentTableColumn.class, id = "name")
public final class AppointmentNameColumn extends RaplaComponent implements AppointmentTableColumn<TableColumn> {
	@Inject
	public AppointmentNameColumn(RaplaContext context) {
		super(context);
	}

	public void init(TableColumn column) {
	
	}

	public Object getValue(AppointmentBlock block) 
	{
		return NameFormatUtil.getName(block, getLocale());
	}

	public String getColumnName() {
		return getString("name");
	}

	public Class<?> getColumnClass() {
		return String.class;
	}

	public String getHtmlValue(AppointmentBlock block) {
		String value = NameFormatUtil.getExportName(block, getLocale());
		return XMLWriter.encode(value);		       

	}
}
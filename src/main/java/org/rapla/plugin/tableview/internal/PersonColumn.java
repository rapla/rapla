package org.rapla.plugin.tableview.internal;

import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.RaplaContext;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.extensionpoints.AppointmentTableColumn;

import javax.inject.Inject;

@Extension(provides = AppointmentTableColumn.class, id = "persons")
public final class PersonColumn extends AllocatableListColumn {
	@Inject
	public PersonColumn(RaplaContext context) {
		super(context);
	}

	@Override
	protected boolean contains(Allocatable alloc)
	{
		return alloc.isPerson();
	}

	public String getColumnName() 
	{
		return getString("persons");
	}
}
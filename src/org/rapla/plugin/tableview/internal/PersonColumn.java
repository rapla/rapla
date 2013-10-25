package org.rapla.plugin.tableview.internal;

import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.RaplaContext;

public final class PersonColumn extends AllocatableListColumn {
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
package org.rapla.plugin.export2ical;

import org.rapla.framework.RaplaException;

public interface ICalExport  {

	 String export(String idString) throws RaplaException;
}
package org.rapla.plugin.export2ical;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;

@WebService
public interface ICalExport  {

	 String export(String idString) throws RaplaException;
}
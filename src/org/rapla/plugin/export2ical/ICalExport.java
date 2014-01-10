package org.rapla.plugin.export2ical;

import javax.jws.WebService;

import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.framework.RaplaException;

@WebService
public interface ICalExport  {

	 String export(SimpleIdentifier[] appointments) throws RaplaException;
}
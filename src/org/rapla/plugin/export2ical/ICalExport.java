package org.rapla.plugin.export2ical;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;
import org.rapla.rest.gwtjsonrpc.common.RemoteJsonService;

@WebService
public interface ICalExport extends RemoteJsonService {

	 String export(String[] appointments) throws RaplaException;
}
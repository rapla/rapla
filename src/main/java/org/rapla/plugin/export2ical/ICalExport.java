package org.rapla.plugin.export2ical;

import org.rapla.framework.RaplaException;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;

@RemoteJsonMethod
public interface ICalExport extends RemoteJsonService  {
	String export(String[] appointments) throws RaplaException;
}
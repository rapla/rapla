package org.rapla.plugin.export2ical;

import org.rapla.framework.RaplaException;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;

@RemoteJsonMethod
public interface ICalExport {
	String export(String[] appointments) throws RaplaException;
}
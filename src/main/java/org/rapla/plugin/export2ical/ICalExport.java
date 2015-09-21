package org.rapla.plugin.export2ical;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;
import org.rapla.inject.Extension;

@RemoteJsonMethod
public interface ICalExport extends RemoteJsonService  {
	String export(String[] appointments) throws RaplaException;
}
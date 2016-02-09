package org.rapla.plugin.export2ical;

import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

import java.util.Collection;
import java.util.Set;

@RemoteJsonMethod
public interface ICalExport {
	String export(Set<String> appointmentIds) throws RaplaException;
}
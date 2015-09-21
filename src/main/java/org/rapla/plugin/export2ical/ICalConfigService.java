package org.rapla.plugin.export2ical;

import javax.jws.WebService;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;

@RemoteJsonMethod
public interface ICalConfigService extends RemoteJsonService  {
    DefaultConfiguration getConfig() throws RaplaException;
    DefaultConfiguration getUserDefaultConfig() throws RaplaException;
}
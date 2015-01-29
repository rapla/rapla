package org.rapla.plugin.export2ical;

import javax.jws.WebService;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.rest.gwtjsonrpc.common.RemoteJsonService;

@WebService
public interface ICalConfigService extends RemoteJsonService  {
    DefaultConfiguration getConfig() throws RaplaException;
}
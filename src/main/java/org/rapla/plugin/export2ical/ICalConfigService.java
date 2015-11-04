package org.rapla.plugin.export2ical;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

@RemoteJsonMethod
public interface ICalConfigService {
    DefaultConfiguration getConfig() throws RaplaException;
    DefaultConfiguration getUserDefaultConfig() throws RaplaException;
}
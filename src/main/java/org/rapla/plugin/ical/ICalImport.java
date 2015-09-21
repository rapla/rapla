package org.rapla.plugin.ical;

import javax.jws.WebService;

import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;

@WebService
@RemoteJsonMethod
public interface ICalImport extends RemoteJsonService {
	 FutureResult<Integer[]> importICal(String content, boolean isURL, String[] allocatableIds, String eventTypeKey, String eventTypeNameAttributeKey);
}
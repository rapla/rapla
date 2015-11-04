package org.rapla.plugin.ical;

import javax.jws.WebService;

import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.RemoteJsonMethod;

@WebService
@RemoteJsonMethod
public interface ICalImport {
	 FutureResult<Integer[]> importICal(String content, boolean isURL, String[] allocatableIds, String eventTypeKey, String eventTypeNameAttributeKey);
}
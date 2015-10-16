package org.rapla.plugin.ical;

import javax.jws.WebService;

import org.rapla.gwtjsonrpc.RemoteJsonMethod;
import org.rapla.gwtjsonrpc.common.FutureResult;

@WebService
@RemoteJsonMethod
public interface ICalImport {
	 FutureResult<Integer[]> importICal(String content, boolean isURL, String[] allocatableIds, String eventTypeKey, String eventTypeNameAttributeKey);
}
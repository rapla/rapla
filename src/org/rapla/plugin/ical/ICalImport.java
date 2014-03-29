package org.rapla.plugin.ical;

import javax.jws.WebService;

import org.rapla.rest.gwtjsonrpc.common.FutureResult;

@WebService
public interface ICalImport  {
	 FutureResult<Integer[]> importICal(String content, boolean isURL, String[] allocatableIds, String eventTypeKey, String eventTypeNameAttributeKey);
}
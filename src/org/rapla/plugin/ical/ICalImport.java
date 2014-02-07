package org.rapla.plugin.ical;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;

@WebService
public interface ICalImport  {
	 Integer[] importICal(String content, boolean isURL, String[] allocatableIds, String eventTypeKey, String eventTypeNameAttributeKey) throws RaplaException;
}
package org.rapla.plugin.ical;

import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.framework.RaplaException;

public interface ICalImport  {
	 Integer[] importICal(String content, boolean isURL, SimpleIdentifier[] allocatableIds, String eventTypeKey, String eventTypeNameAttributeKey) throws RaplaException;
}
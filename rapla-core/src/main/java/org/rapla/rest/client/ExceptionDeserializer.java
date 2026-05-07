package org.rapla.rest.client;

import org.rapla.rest.SerializableExceptionInformation;

public interface ExceptionDeserializer {
	Exception deserializeException(SerializableExceptionInformation exceptionInformation,int responseCode);
}

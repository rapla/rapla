package org.rapla.rest.gwtjsonrpc.client;

import java.util.List;

public interface ExceptionDeserializer {

	Exception deserialize(String exception, String message, List<String> parameter);

}

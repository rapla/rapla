package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

public class RaplaWrongVersionException extends RaplaException {

	private static final long serialVersionUID = 1L;

	public RaplaWrongVersionException(String text) {
		super(text);
	}


}

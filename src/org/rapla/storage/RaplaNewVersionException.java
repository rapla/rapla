package org.rapla.storage;

import org.rapla.framework.RaplaException;

public class RaplaNewVersionException extends RaplaException {

	private static final long serialVersionUID = 1L;

	public RaplaNewVersionException(String text) {
		super(text);
	}

}

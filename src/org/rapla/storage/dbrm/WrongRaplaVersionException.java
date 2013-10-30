package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

public class WrongRaplaVersionException extends RaplaException {

	private static final long serialVersionUID = 1L;

	public WrongRaplaVersionException(String text) {
		super(text);
	}


}

package org.rapla.facade;

import org.rapla.framework.RaplaException;

public class CalendarNotFoundExeption extends RaplaException {

	public CalendarNotFoundExeption(String text) {
		super(text);
	}

	private static final long serialVersionUID = 1L;

}

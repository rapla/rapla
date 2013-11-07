package org.rapla.framework;

public class RaplaSynchronizationException extends RaplaException {

	private static final long serialVersionUID = 1L;

	public RaplaSynchronizationException(String text) {
		super(text);
	}

	public RaplaSynchronizationException(Throwable ex) {
		super( ex.getMessage(), ex);
	}

}

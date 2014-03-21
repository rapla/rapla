package org.rapla.entities.storage;

public class UnresolvableReferenceExcpetion extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UnresolvableReferenceExcpetion(String id) {
		super("Can't resolve reference for id " + id );
	}
	
	public UnresolvableReferenceExcpetion(String id, String reference) {
		super("Can't resolve reference for id " + id  + " from refererer " + reference);
	}

}

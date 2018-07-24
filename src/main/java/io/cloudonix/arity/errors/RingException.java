package io.cloudonix.arity.errors;

public class RingException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public RingException(Throwable t) {
		super(t);
	}
}

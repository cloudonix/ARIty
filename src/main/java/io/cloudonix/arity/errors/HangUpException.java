package io.cloudonix.arity.errors;

public class HangUpException extends  Exception{
	
	private static final long serialVersionUID = 1L;
	
	public HangUpException() {
		super();
	}
	
	public HangUpException (Throwable t) {
		super(t);
	}
	
}

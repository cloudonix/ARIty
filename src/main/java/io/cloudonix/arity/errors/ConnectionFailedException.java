package io.cloudonix.arity.errors;

public class ConnectionFailedException  extends Exception{

	private static final long serialVersionUID = 1L;
	
	public ConnectionFailedException() {
		super();
	}
	
	public ConnectionFailedException (Throwable t) {
		super(t);	
	}
	
	
}

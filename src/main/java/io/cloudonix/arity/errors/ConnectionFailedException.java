package io.cloudonix.arity.errors;

/**
 * The class throws an exception when the connection to ARI failed
 * @author naamag
 *
 */
public class ConnectionFailedException  extends Exception{
	private static final long serialVersionUID = 1L;
	
	public ConnectionFailedException() {
		super();
	}
	
	public ConnectionFailedException (Throwable t) {
		super(t);	
	}
}

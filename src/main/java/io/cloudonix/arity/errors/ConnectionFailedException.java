package io.cloudonix.arity.errors;

/**
 * The class throws an exception when the connection to ARI failed
 * @author naamag
 *
 */
public class ConnectionFailedException extends ARItyException {
	private static final long serialVersionUID = 1L;
	
	public ConnectionFailedException (Throwable t) {
		super(t);	
	}
}

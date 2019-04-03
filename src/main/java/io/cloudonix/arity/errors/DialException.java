package io.cloudonix.arity.errors;

/**
 * The class throws an exception when Dial operation failed
 * @author naamag
 *
 */
public class DialException extends Exception {
	
private static final long serialVersionUID = 1L;
	
	public DialException() {
		super();
	}
	
	public DialException (Throwable t) {
		super(t);
	}
}

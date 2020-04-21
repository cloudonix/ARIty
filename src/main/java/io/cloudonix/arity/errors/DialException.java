package io.cloudonix.arity.errors;

/**
 * The class throws an exception when Dial operation failed
 * @author naamag
 *
 */
public class DialException extends ARItyException {
	
	private static final long serialVersionUID = 1L;
	
	public DialException (Throwable t) {
		super(t);
	}
	
	public DialException (String m, Throwable t) {
		super(m, t);
	}
}

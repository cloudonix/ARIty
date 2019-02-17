package io.cloudonix.arity.errors;

/**
 * throw exception when we fail to redirect the channel
 * 
 * @author naamag
 *
 */
public class RedirectException extends Exception {
	private static final long serialVersionUID = 1L;

	public RedirectException(String message) {
		super(message);
	}
}

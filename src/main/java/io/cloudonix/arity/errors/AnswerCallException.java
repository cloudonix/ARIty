package io.cloudonix.arity.errors;

/**
 * The class throws an exception in case that a call can not be answered
 * @author naamag
 *
 */

public class AnswerCallException extends Exception {
	
	private static final long serialVersionUID = 1L;
	
	public AnswerCallException (Throwable t) {
		super(t);
	}
	

}


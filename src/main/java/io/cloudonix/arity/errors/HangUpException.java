package io.cloudonix.arity.errors;

/**
 * The class throws an exception when a call can not be hanged up
 * @author naamag
 *
 */
public class HangUpException extends  Exception{
	
	private static final long serialVersionUID = 1L;
	
	public HangUpException() {
		super();
	}
	
	public HangUpException (Throwable t) {
		super(t);
	}
	
}

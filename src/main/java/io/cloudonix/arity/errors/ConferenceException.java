package io.cloudonix.arity.errors;

/**
 * Throw an exception for conference possible failures
 * 
 * @author naamag
 *
 */
public class ConferenceException extends ARItyException {
	private static final long serialVersionUID = 1L;
	
	public ConferenceException(String errorMessage) {
		super(errorMessage);
	}

	public ConferenceException(Throwable cause) {
		super(cause);
	}
}

package io.cloudonix.arity.errors;

import ch.loway.oss.ari4java.tools.ARIException;

@SuppressWarnings("serial")
public class ARItyException extends ARIException {

	public ARItyException(Throwable cause) {
		super(cause);
	}

	public ARItyException(String message) {
		super(message);
	}

	public ARItyException(String message, Throwable cause) {
		super(new Exception(message, cause));
	}

}

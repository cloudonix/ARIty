package io.cloudonix.arity.errors.dial;

import io.cloudonix.arity.errors.ARItyException;

@SuppressWarnings("serial")
public class FailedToAnswerChannel extends ARItyException {

	public FailedToAnswerChannel(Throwable cause) {
		super(cause);
	}

}

package io.cloudonix.arity.errors.dial;

import java.util.concurrent.CompletionException;

@SuppressWarnings("serial")
public class FailedToAnswerChannel extends CompletionException {

	public FailedToAnswerChannel(Throwable cause) {
		super(cause);
	}

}

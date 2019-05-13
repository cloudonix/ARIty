package io.cloudonix.arity.errors.dial;

import java.util.concurrent.CompletionException;

@SuppressWarnings("serial")
public class ChannelNotFoundException extends CompletionException {

	public ChannelNotFoundException(Throwable cause) {
		super(cause);
	}

}

package io.cloudonix.arity.errors.bridge;

import java.util.concurrent.CompletionException;

@SuppressWarnings("serial")
public class ChannelNotInBridgeException extends CompletionException {

	public ChannelNotInBridgeException(Throwable cause) {
		super(cause);
	}

}

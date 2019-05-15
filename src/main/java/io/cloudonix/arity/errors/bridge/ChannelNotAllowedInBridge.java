package io.cloudonix.arity.errors.bridge;

import java.util.concurrent.CompletionException;

@SuppressWarnings("serial")
public class ChannelNotAllowedInBridge extends CompletionException {

	public ChannelNotAllowedInBridge(String message) {
		super(message);
	}

}

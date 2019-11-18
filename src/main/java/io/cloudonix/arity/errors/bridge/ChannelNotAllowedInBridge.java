package io.cloudonix.arity.errors.bridge;

import io.cloudonix.arity.errors.ARItyException;

@SuppressWarnings("serial")
public class ChannelNotAllowedInBridge extends ARItyException {

	public ChannelNotAllowedInBridge(String message) {
		super(message);
	}

}

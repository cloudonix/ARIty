package io.cloudonix.arity.errors.bridge;

import io.cloudonix.arity.errors.ARItyException;

@SuppressWarnings("serial")
public class ChannelNotInBridgeException extends ARItyException {

	public ChannelNotInBridgeException(Throwable cause) {
		super(cause);
	}

}

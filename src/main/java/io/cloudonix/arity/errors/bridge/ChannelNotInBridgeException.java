package io.cloudonix.arity.errors.bridge;

import io.cloudonix.arity.errors.ARItyException;

@SuppressWarnings("serial")
public class ChannelNotInBridgeException extends ARItyException {

	private String bridgeId;

	public ChannelNotInBridgeException(String bridgeId, Throwable cause) {
		super(cause.getMessage() + " (bridge:" + bridgeId + ")", cause);
		this.bridgeId = bridgeId;
	}

	public String getBridgeId() {
		return bridgeId;
	}
}

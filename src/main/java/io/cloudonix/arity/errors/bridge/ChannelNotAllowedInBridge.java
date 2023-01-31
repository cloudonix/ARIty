package io.cloudonix.arity.errors.bridge;

import io.cloudonix.arity.errors.ARItyException;

@SuppressWarnings("serial")
public class ChannelNotAllowedInBridge extends ARItyException {

	private String bridgeId;

	public ChannelNotAllowedInBridge(String bridgeId, String message) {
		super(message + " (bridge:" + bridgeId + ")");
		this.bridgeId = bridgeId;
	}

	public String getBridgeId() {
		return bridgeId;
	}
}

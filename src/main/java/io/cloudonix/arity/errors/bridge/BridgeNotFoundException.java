package io.cloudonix.arity.errors.bridge;

import io.cloudonix.arity.errors.ARItyException;

@SuppressWarnings("serial")
public class BridgeNotFoundException extends ARItyException {

	private String bridgeId;

	public BridgeNotFoundException(String bridgeId, Throwable cause) {
		super(cause.getMessage() + " (" + bridgeId + ")");
		this.bridgeId = bridgeId;
	}
	
	public String getBridgeId() {
		return bridgeId;
	}
}

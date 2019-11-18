package io.cloudonix.arity.errors.bridge;

import io.cloudonix.arity.errors.ARItyException;

@SuppressWarnings("serial")
public class BridgeNotFoundException extends ARItyException {

	public BridgeNotFoundException(Throwable cause) {
		super(cause);
	}
}

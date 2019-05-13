package io.cloudonix.arity.errors.bridge;

import java.util.concurrent.CompletionException;

@SuppressWarnings("serial")
public class BridgeNotFoundException extends CompletionException {

	public BridgeNotFoundException(Throwable cause) {
		super(cause);
	}
}

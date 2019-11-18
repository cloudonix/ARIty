package io.cloudonix.arity.errors.dial;

import io.cloudonix.arity.errors.ARItyException;

@SuppressWarnings("serial")
public class ChannelNotFoundException extends ARItyException {

	public ChannelNotFoundException(Throwable cause) {
		super(cause);
	}

}

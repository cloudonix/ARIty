package io.cloudonix.arity.errors.dial;

import io.cloudonix.arity.errors.ARItyException;

/**
 * Transitional exception class after moving the original to the errors package where it can be auto
 * loaded by {@link ARItyException#ariRestExceptionMapper(Throwable)}.
 * 
 * @deprecated please use {@link io.cloudonix.arity.errors.ChannelNotFoundException} instead. This will be removed in a future release
 */
@Deprecated
public class ChannelNotFoundException extends io.cloudonix.arity.errors.ChannelNotFoundException {
	
	private static final long serialVersionUID = -4602001101879520741L;

	public ChannelNotFoundException(Throwable cause) {
		super(cause);
	}

}

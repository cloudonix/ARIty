package io.cloudonix.arity.errors;

/**
 * A channel in invalid state variation that we get when we try to operation on a channel that
 * has not yet entered, or has left, stasis.
 */
public class ChannelNotInStasisApplicationException extends ChannelInInvalidState {

	private static final long serialVersionUID = -1389642525954260729L;

	public ChannelNotInStasisApplicationException(Throwable cause) {
		super(cause);
	}
}

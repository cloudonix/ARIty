package io.cloudonix.arity.errors;

import ch.loway.oss.ari4java.tools.RestException;

/**
 * throw Hang up exception when failed to hang up
 * 
 * @author naamag
 *
 */
public class HangUpException extends Throwable {
	private static final long serialVersionUID = 1L;

	public HangUpException(RestException e) {
		super(e);
	}
}

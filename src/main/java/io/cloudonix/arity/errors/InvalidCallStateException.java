package io.cloudonix.arity.errors;

public class InvalidCallStateException extends RuntimeException {

	private static final long serialVersionUID = -5377544466514081357L;

	public InvalidCallStateException() {
		super("Call state is invalid or not initialized");
	}

}

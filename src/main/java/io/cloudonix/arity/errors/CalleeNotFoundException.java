package io.cloudonix.arity.errors;

public class CalleeNotFoundException extends ChannelNotFoundException {

	private static final long serialVersionUID = 5665365668934793559L;

	public CalleeNotFoundException(Throwable cause) {
		super(cause);
	}

}

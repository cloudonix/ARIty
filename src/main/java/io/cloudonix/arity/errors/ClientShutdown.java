package io.cloudonix.arity.errors;

public class ClientShutdown extends ARItyException {

	private static final long serialVersionUID = 5665365668934793559L;

	public ClientShutdown(Throwable cause) {
		super(cause);
	}

}

package io.cloudonix.arity.errors;

public class ChannelInInvalidState extends ARItyException {
	private static final long serialVersionUID = -252582828763217913L;

	public ChannelInInvalidState(Throwable ariError) {
		super(ariError);
	}
}

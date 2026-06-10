package io.cloudonix.arity.errors;

import java.time.Instant;

public class AllocationFailedException extends ARItyException {

	private static final long serialVersionUID = 6447347870663573855L;

	public AllocationFailedException() {
		super("Allocation failed in asterisk (see asterisk logs " + Instant.now().toString() + ")");
	}

}

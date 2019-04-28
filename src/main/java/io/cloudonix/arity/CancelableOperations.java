package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;

/**
 * This class is for operations that can be cancelled, such as Dial, Play etc.
 * 
 * @author naamag
 *
 */
public abstract class CancelableOperations extends Operation {

	public CancelableOperations(String chanId, ARIty s) {
		super(chanId, s);
	}

	/**
	 * cancel this operation
	 * 
	 * @return
	 */
	abstract public CompletableFuture<Void> cancel();
}

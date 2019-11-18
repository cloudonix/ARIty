package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

/**
 * Ring the call controller's channel.
 * 
 * This implementation is a "best effort" operation - as Asterisk might fail to ring the channel if it is in the wrong
 * status, such as "already ringing", and for most applications it is enough - so the default {@link #run()} hides
 * any errors during ARI operations.
 * 
 * If you are interested in handling errors thrown during the ARI ring operation, use the alternative {@link #run(boolean)
 * 
 * @author naamag
 * @author odeda
 *
 */
public class Ring extends CancelableOperations {

	private final static Logger logger = Logger.getLogger(Answer.class.getName());
	private String channelId;

	/**
	 * Create a new ringing operation
	 * 
	 * @param callController the call channel that should be ringing
	 */
	public Ring(CallController callController) {
		super(callController.getChannelId(), callController.getARIty());
		this.channelId = callController.getChannelId();
	}

	@Override
	public CompletableFuture<Ring> run() {
		return run(false);
	}
	
	/**
	 * Alternative to {@link #run()} that can be instructed to not hide errors in the ring operation
	 * @param throwError should errors encountered while trying to ring the channel should be reported
	 * 	to the caller using a rejected promise.
	 * @return a promise for completing the ring operation - could be rejected if <code>throwError</code> is <code>true</code>
	 */
	public CompletableFuture<Ring> run(boolean throwError) {
		return this.<Void>retryOperation(h -> channels().ring(channelId, h))
				.handle((v,t) -> {
					if (Objects.isNull(t)) logger.fine("Ringing");
					else if (throwError) throw new CompletionException(t);
					else logger.warning("Failed ringing: " + t);
					return this;
				});
	}

	@Override
	public CompletableFuture<Void> cancel() {
		return this.<Void>retryOperation(h -> channels().ringStop(channelId, h))
				.whenComplete((v,t) -> {
					if (Objects.isNull(t)) logger.fine("Stoped ringing to channel with id: " + channelId);
					else logger.warning("Failed to stop ringing to channel with id " + channelId + ": " + t);
				});
	}
}

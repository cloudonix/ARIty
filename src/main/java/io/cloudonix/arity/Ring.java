package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Class that execute channel ring operation
 * 
 * @author naamag
 *
 */
public class Ring extends CancelableOperations {

	private final static Logger logger = Logger.getLogger(Answer.class.getName());
	private String channelId;

	/**
	 * Constructor 
	 * 
	 * @param callController instance of CallController
	 */
	public Ring(CallController callController) {
		super(callController.getChannelId(), callController.getARIty());
		this.channelId = callController.getChannelId();
	}

	/**
	 * The method ring to channel
	 */
	@Override
	public CompletableFuture<Ring> run() {
		return Operation.<Void>retryOperation(h -> channels().ring(channelId, h))
				.whenComplete((v,t) -> {
					if (Objects.isNull(t)) logger.fine("Ringing");
					else logger.warning("Failed ringing: " + t);
				})
				.thenApply(v -> this);
	}

	@Override
	public CompletableFuture<Void> cancel() {
		return Operation.<Void>retryOperation(h -> channels().ringStop(channelId, h))
				.whenComplete((v,t) -> {
					if (Objects.isNull(t)) logger.fine("Stoped ringing to channel with id: " + channelId);
					else logger.warning("Failed to stop ringing to channel with id " + channelId + ": " + t);
				});
	}
}

package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.lib.Futures;

/**
 * Class that execute muting/unmuting a channel
 *
 * @author naamag
 *
 */
public class Mute extends CancelableOperations {

	private final static Logger logger = Logger.getLogger(Answer.class.getName());
	private String channelId;
	private String direction;

	public Mute(CallController callController, String channelId, String direction) {
		super(callController.getChannelId(), callController.getARIty());
		this.channelId = channelId;
		this.direction = direction;
	}

	@Override
	public CompletableFuture<Mute> run() {
		return this.<Void>retryOperation(cb -> channels().mute(channelId).setDirection(direction).execute(cb))
				.thenAccept(v -> {
					logger.info("Muted channel with id: " + channelId + " and muted audio in dirction: " + direction);
				})
				.exceptionally(Futures.on(RestException.class,  e -> {
					logger.warning("Failed to mute channel with id: " + channelId + " and direction: " + direction);
					throw e;
				}))
				.thenApply(v -> this);
	}

	@Override
	public CompletableFuture<Void> cancel() {
		return this.<Void>retryOperation(cb -> channels().unmute(channelId).setDirection(direction).execute(cb))
				.thenAccept(res -> {
					logger.info("Unmute channel " + channelId + " with audio direction " + direction);
				})
				.exceptionally(Futures.on(RestException.class,  e -> {
					logger.warning("Failed to unmute channel with id: " + channelId + " and direction: " + direction);
					throw e;
				}));
	}

}

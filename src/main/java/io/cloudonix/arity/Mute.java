package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;

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
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.channelId = channelId;
		this.direction = direction;
	}

	@Override
	public CompletableFuture<Mute> run() {
		CompletableFuture<Void> future = new CompletableFuture<Void>();
		getAri().channels().mute(channelId, direction, new AriCallback<Void>() {

			@Override
			public void onSuccess(Void result) {
				logger.info("Muted channel with id: " + channelId + " and muted audio in dirction: " + direction);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("Failed to unmute channel with id: " + channelId + " and audio direction: " + direction);
				future.completeExceptionally(e);
			}
		});

		return future.thenApply(v -> this);
	}

	@Override
	void cancel() {
		getAri().channels().unmute(channelId, direction, new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.info("Unmute channel " + channelId + " with audio direction " + direction);
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("Failed to unmute channel with id: " + channelId + " and direction: " + direction);
			}
		});
	}

}

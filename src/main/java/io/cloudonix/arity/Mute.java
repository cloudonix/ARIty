package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.helpers.Futures;
import io.cloudonix.arity.models.AsteriskChannel;

/**
 * Class that execute muting/unmuting a channel
 *
 * @author naamag
 *
 */
public class Mute extends CancelableOperations {
	
	public static final AsteriskChannel.Mute BOTH = AsteriskChannel.Mute.BOTH;
	public static final AsteriskChannel.Mute IN = AsteriskChannel.Mute.IN;
	public static final AsteriskChannel.Mute OUT = AsteriskChannel.Mute.OUT;
	public static final AsteriskChannel.Mute NO = AsteriskChannel.Mute.NO;

	private final static Logger logger = LoggerFactory.getLogger(Mute.class);
	private AsteriskChannel.Mute direction;

	public Mute(CallController callController, AsteriskChannel.Mute direction) {
		super(callController.getChannelId(), callController.getARIty());
		this.direction = direction;
	}

	@Override
	public CompletableFuture<Mute> run() {
		return this.<Void>retryOperation(cb -> executeMute(cb))
				.thenAccept(v -> {
					logger.info("Muted channel with id: {} and muted audio in dirction: {}", getChannelId(), direction);
				})
				.exceptionally(Futures.on(RestException.class,  e -> {
					logger.warn("Failed to mute channel with id: {} and direction: {}", getChannelId(), direction);
					throw e;
				}))
				.thenApply(v -> this);
	}

	private void executeMute(AriCallback<Void> cb) throws RestException {
		if (direction == NO) {
			channels().unmute(getChannelId()).setDirection(BOTH.value()).execute(cb);
			return;
		}
		channels().mute(getChannelId()).setDirection(direction.value()).execute(cb);
	}

	@Override
	public CompletableFuture<Void> cancel() {
		return this.<Void>retryOperation(cb -> channels().unmute(getChannelId()).setDirection(BOTH.value()).execute(cb))
				.thenAccept(res -> {
					logger.info("Unmute channel {} with audio direction {}", getChannelId(), direction);
				})
				.exceptionally(Futures.on(RestException.class,  e -> {
					logger.warn("Failed to unmute channel with id: {} and direction: {}", getChannelId(), direction);
					throw e;
				}));
	}

}

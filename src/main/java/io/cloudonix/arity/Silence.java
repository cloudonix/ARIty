package io.cloudonix.arity;

import io.cloudonix.arity.helpers.Futures;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class Silence extends CancelableOperations {
	
	private final Duration duration;
	
	private final AtomicBoolean playing = new AtomicBoolean(false);
	
	public Silence(final CallController callController, final Duration duration) {
		super(callController.getChannelId(), callController.getARIty());
		this.duration = duration;
	}
	
	@Override
	public CompletableFuture<Void> cancel() {
		return stop();
	}
	
	@Override
	public CompletableFuture<? extends Operation> run() {
		return this.<Void>retryOperation(h -> channels().startSilence(getChannelId()).execute(h))
			.thenAccept(__ -> playing.set(true))
			.thenCompose(Futures.delay(duration.toMillis()))
			.thenCompose(__ -> stop())
			.thenApply(__ -> this);
	}
	
	private CompletableFuture<Void> stop() {
		if (!playing.compareAndSet(true, false))
			return CompletableFuture.completedFuture(null);
		
		return retryOperation(h -> channels().stopSilence(getChannelId()).execute(h));
	}
}

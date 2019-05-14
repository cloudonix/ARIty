package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.loway.oss.ari4java.generated.ActionChannels;
import ch.loway.oss.ari4java.generated.ActionPlaybacks;
import ch.loway.oss.ari4java.generated.ActionRecordings;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.lib.Futures;

/**
 * A general class that represents an Asterisk operation
 * 
 * @author naamag
 *
 */
public abstract class Operation {
	private static final long RETRY_TIME = 1000;
	private static final int RETRIES = 5;
	private String channelId;
	private ARIty arity;

	/**
	 * Constructor
	 * 
	 * @param channelId id of the channel or bridge
	 * @param arity     instance of ARIty
	 * @param ari       ARI instance
	 */
	public Operation(String channelId, ARIty arity) {
		this.channelId = channelId;
		this.arity = arity;
	}

	/**
	 * get id of the channel
	 * 
	 * @return
	 */
	public String getChannelId() {
		return channelId;
	}

	/**
	 * get arity
	 * 
	 * @return
	 */
	public ARIty getArity() {
		return arity;
	}

	public abstract CompletableFuture<? extends Operation> run();

	/**
	 * Convert an ari4java async operation (with onSuccess/onFailure callback) to a Java 8 {@link CompletableFuture}
	 * 
	 * @param op a Lambda that takes a one-off {@link AriCallback} instance and uses it to run an ARI operation
	 * @return a promise for the completion of the ARI operation
	 */
	private static <V> CompletableFuture<V> toFuture(Consumer<AriCallback<V>> op) {
		StackTraceElement[] caller = getCallingStack();
		CompletableFuture<V> cf = new CompletableFuture<V>();
		AriCallback<V> ariCallback = new AriCallback<V>() {

			@Override
			public void onSuccess(V result) {
				cf.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				cf.completeExceptionally(rewrapError("ARI operation failed: " + e, caller, e));
			}
		};

		op.accept(ariCallback);
		return cf;
	}

	/**
	 * set the channel id on which we do the operation
	 * 
	 * @param channelId new channel id
	 */
	public void setChannelId(String channelId) {
		this.channelId = channelId;
	}

	public static StackTraceElement getCallingFrame() {
		return Stream.of(new Exception().fillInStackTrace().getStackTrace()).skip(2).findFirst().orElse(null);
	}

	public static StackTraceElement[] getCallingStack() {
		return Stream.of(new Exception().fillInStackTrace().getStackTrace()).skip(1).collect(Collectors.toList())
				.toArray(new StackTraceElement[] {});
	}
	
	public static CompletionException rewrapError(String message, StackTraceElement[] originalStack, Throwable cause) {
		while (cause instanceof CompletionException)
			cause = cause.getCause();
		CompletionException wrap = new CompletionException(message, cause);
		wrap.setStackTrace(originalStack);
		return wrap;
	}

	/**
	 * retry to execute ARI operation few times
	 * 
	 * @param op the ARI operation to execute
	 * 
	 * @return
	 */
	
	public static <V> CompletableFuture<V> retryOperation(Consumer<AriCallback<V>> op) {
		return retryOperation(op, RETRIES);
	}
	
	public static <V> CompletableFuture<V> retryOperation(Consumer<AriCallback<V>> op, int triesLeft) {
		StackTraceElement[] caller = getCallingStack();
		return toFuture(op).handle((v,t) -> {
			if (Objects.isNull(t)) 
				return Futures.completedFuture(v);
			if (triesLeft <= 0 || !(t.getMessage().toLowerCase().contains("timeout")))
				return Futures.<V>failedFuture(t);
			return Futures.delay(RETRY_TIME).apply(null)
					.thenCompose(v1->retryOperation(op, triesLeft - 1));
		})
		.thenCompose(x -> x)
		.exceptionally(t -> { throw rewrapError("Unrecoverable ARI operation error: " + t, caller, t); });
	}
	
	protected ActionChannels channels() {
		return arity.getAri().channels();
	}
	
	protected ActionPlaybacks playbacks() {
		return arity.getAri().playbacks();
	}
	
	protected ActionRecordings recordings() {
		return arity.getAri().recordings();
	}
}

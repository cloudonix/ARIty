package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.loway.oss.ari4java.generated.actions.ActionChannels;
import ch.loway.oss.ari4java.generated.actions.ActionPlaybacks;
import ch.loway.oss.ari4java.generated.actions.ActionRecordings;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.dial.ChannelNotFoundException;
import io.cloudonix.lib.Futures;

/**
 * A general class that represents an Asterisk operation
 *
 * @author naamag
 * @author odeda
 */
public abstract class Operation {

	@FunctionalInterface
	public static interface AriOperation<T> {
		void accept(AriCallback<T> t) throws RestException;
	}

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
	private static <V> CompletableFuture<V> toFuture(AriOperation<V> op) {
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

		try {
			op.accept(ariCallback);
		} catch (RestException e1) {
			cf.completeExceptionally(e1);
		}
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
	 * Retry to execute ARI operation few times
	 *
	 * This method is for use by {@code Operation} implementations and will use the implementation's
	 * {@link #tryIdentifyError(Throwable)} implementation as the failure handler. See the linked method's
	 * docs for default implementation notes.
	 *
	 * @param op the ARI operation to execute
	 * @return result of the operation, if successful, or a failure if the operation failed all retries, or the
	 *   current operation implementation determined an error to be fatal without retrying.
	 */
	public <V> CompletableFuture<V> retryOperation(AriOperation<V> op) {
		return retryOperationImpl(op, RETRIES, this::tryIdentifyError);
	}

	/**
	 * Retry to execute ARI operation few times
	 *
	 * @param op the ARI operation to execute
	 * @return result of the operation, if successful, or a failure if the operation failed all retries
	 */
	public static <V> CompletableFuture<V> retry(AriOperation<V> op) {
		return retryOperationImpl(op, RETRIES, v -> null);
	}

	/**
	 * Retry to execute ARI operation few times, failing without retries if the exception is determined fatal
	 * by the provided exception mapper
	 *
	 * @param op the ARI operation to execute
	 * @param exceptionMapper user provided logic to determine if an error should be retried. If the provided
	 *   function returns {@code null}, then the operation will be retried, otherwise the returned exception will
	 *   be propagated as the failure.
	 * @return result of the operation, if successful, or a failure if the operation failed all retries
	 */
	public static <V> CompletableFuture<V> retry(AriOperation<V> op, Function<Throwable, Exception> exceptionMapper) {
		return retryOperationImpl(op, RETRIES, exceptionMapper);
	}

	/**
	 * Retry to execute ARI operation few times - internal implementation
	 *
	 * @param op the ARI operation to execute
	 * @param triesLeft Number of tries left before determining the failure to be fatal
	 * @param exceptionMapper user provided logic to determine if an error should be retried. If the provided
	 *   function returns {@code null}, then the operation will be retried, otherwise the returned exception will
	 *   be propagated as the failure.
	 * @return result of the operation, if successful, or a failure if the operation failed all retries, or
	 *   the provided exception mapper determined the exception to be fatal before retrying
	 */
	private static <V> CompletableFuture<V> retryOperationImpl(AriOperation<V> op, int triesLeft,
			Function<Throwable, Exception> exceptionMapper) {
		StackTraceElement[] caller = getCallingStack();
		return toFuture(op).handle((v,t) -> {
			if (Objects.isNull(t))
				return Futures.completedFuture(v);
			Exception recognizedFailure = exceptionMapper.apply(unwrapCompletionError(t));
			if (Objects.nonNull(recognizedFailure))
				throw rewrapError("Unrecoverable ARI operation error: " + recognizedFailure, caller, recognizedFailure);
			if (triesLeft <= 0 || !(t.getMessage().toLowerCase().contains("timeout")))
				throw rewrapError("Unrecoverable ARI operation error: " + t, caller, t);
			return Futures.delay(RETRY_TIME).apply(null)
					.thenCompose(v1->retryOperationImpl(op, triesLeft - 1, exceptionMapper));
		})
		.thenCompose(x -> x);
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

	/**
	 * Check whether an ARI operation exception, provided as the parameter to the method, should be considered
	 * a terminal error that should not be retried.
	 *
	 * this method is called on by {@link #retryOperation(Consumer)} to check if an ARI error received when trying
	 * the operation is fatal in the context of the specific {@code Operation} implementation (i.e. - should not be
	 * retried). If the method returns {@code null}, then the operation will be retried, otherwise the exception
	 * returned by this method will be propagated to the caller of the operation.
	 *
	 * Currently the following general errors are considered "fatal":
	 *  - {@code "Channel not found"}
	 * @param ariError error to check
	 * @return an exception, if the operation should not be retried
	 */
	protected Exception tryIdentifyError(Throwable ariError) {
		switch (ariError.getMessage()) {
		case "Channel not found": return new ChannelNotFoundException(ariError);
		}
		return null;
	}

	protected static Throwable unwrapCompletionError(Throwable error) {
		while (error instanceof CompletionException) {
			Throwable cause = error.getCause();
			if (Objects.isNull(cause))
				return error;
			error = error.getCause();
		}
		return error;
	}
}

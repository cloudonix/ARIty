package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.actions.ActionBridges;
import ch.loway.oss.ari4java.generated.actions.ActionChannels;
import ch.loway.oss.ari4java.generated.actions.ActionPlaybacks;
import ch.loway.oss.ari4java.generated.actions.ActionRecordings;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ARItyException;
import io.cloudonix.arity.errors.InvalidCallStateException;
import io.cloudonix.arity.helpers.Futures;

/**
 * A general class that represents an Asterisk operation
 *
 * @author naamag
 * @author odeda
 */
public abstract class Operation {

	private static final Logger log = LoggerFactory.getLogger(Operation.class);

	@FunctionalInterface
	public static interface AriOperation<T> {
		void accept(AriCallback<T> t) throws RestException;
	}

	private static final long RETRY_TIME = 100;
	private static final int RETRIES = 3;
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

	private static ExecutorService opDispatch = Executors.newCachedThreadPool();
	
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
				CompletableFuture.runAsync(() -> cf.complete(result), opDispatch);
			}

			@Override
			public void onFailure(RestException e) {
				CompletableFuture.runAsync(() -> cf.completeExceptionally(rewrapError("ARI operation failed: " + e, caller, e)), opDispatch);
			}
		};

		try {
			op.accept(ariCallback);
		} catch (RestException e1) {
			CompletableFuture.runAsync(() -> cf.completeExceptionally(rewrapError("ARI operation failed: " + e1, caller, e1)), opDispatch);
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
				.toArray(StackTraceElement[]::new);
	}
	
	public static String getLastSignificantCaller(StackTraceElement[] callstack) {
		return Stream.of(callstack).filter(s -> !s.getClassName().contentEquals(Operation.class.getName())).findFirst()
				.map(s -> s.toString()).orElse("unknown");
	}

	public static CompletionException rewrapError(String message, StackTraceElement[] originalStack, Throwable cause) {
		while (cause instanceof CompletionException)
			cause = cause.getCause();
		CompletionException wrap = new CompletionException(message, cause);
		wrap.fillInStackTrace();
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
	 * Alias to {@link #retryOperation(AriOperation)} to force the generic type in a possibly more readable way
	 * @param <V>  The generic type of the resulting promise's value
	 * @param type the class for the generic type - this is just used for enforcing the type and its value is not used
	 * @param op the ARI operation to execute
	 * @return result of the operation, if successful, or a failure if the operation failed all retries, or the
	 *   current operation implementation determined an error to be fatal without retrying.
	 */
	public <V> CompletableFuture<V> retryOperation(Class<V> type, AriOperation<V> op) {
		return retryOperationImpl(op, RETRIES, this::tryIdentifyError);
	}

	/**
	 * Retry to execute ARI operation few times
	 *
	 * @param op the ARI operation to execute
	 * @return result of the operation, if successful, or a failure if the operation failed all retries
	 */
	public static <V> CompletableFuture<V> retry(AriOperation<V> op) {
		return retryOperationImpl(op, RETRIES, ARItyException::ariRestExceptionMapper);
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
		return retryOperationImpl(op, triesLeft, exceptionMapper, caller);
	}
	
	private static <V> CompletableFuture<V> retryOperationImpl(AriOperation<V> op, int triesLeft,
			Function<Throwable, Exception> exceptionMapper, StackTraceElement[] caller) {
		Supplier<CompletableFuture<V>> retrier = () -> Futures.delay(RETRY_TIME).apply(null)
				.thenCompose(v1->retryOperationImpl(op, triesLeft - 1, exceptionMapper, caller));
		return toFuture(op).handle((v,t) -> {
			if (t == null)
				return CompletableFuture.completedFuture(v);
			Exception recognizedFailure = exceptionMapper.apply(unwrapCompletionError(t));
			if (recognizedFailure != null) {
				recognizedFailure.setStackTrace(caller);
				throw rewrapError("Unrecoverable ARI operation error: " + recognizedFailure, caller, recognizedFailure);
			}
			if (triesLeft <= 0)
				throw rewrapError("Unrecoverable ARI operation error (no more retries): " + t, caller, t);
			RestException restCause = findRestException(t);
			if (restCause.getCode() >= 500) {// Asterisk error - no need to check mapping, we should immediately try again
				log.warn("[from {}] ARI {}, retrying: {}", getLastSignificantCaller(caller), restCause, restCause.getResponse());
				return retrier.get();
			}
			if (t.getMessage().toLowerCase().contains("timeout")) {
				log.warn("[from [}] ARI timeout: {}", getLastSignificantCaller(caller), t.getMessage());
				return retrier.get();
			}
			if (t.getMessage().contains("Client Shutdown")) {
				log.warn("[from [}] ARI client shutdown: {}", getLastSignificantCaller(caller), t.getMessage());
				return retrier.get();
			}
			throw rewrapError("Unexpected ARI operation error: " + t, caller, t);
		})
		.thenCompose(x -> x);
	}
	
	private static RestException findRestException(Throwable t) {
		while (t != null) {
			if (t instanceof RestException)
				return (RestException) t;
			t = t.getCause();
		}
		return null;
	}

	protected ActionChannels channels() {
		if (arity == null || arity.getAri() == null)
				throw new InvalidCallStateException();
		return arity.getAri().channels();
	}

	protected ActionBridges bridges() {
		if (arity == null || arity.getAri() == null)
				throw new InvalidCallStateException();
		return arity.getAri().bridges();
	}

	protected ActionPlaybacks playbacks() {
		if (arity == null || arity.getAri() == null)
				throw new InvalidCallStateException();
		return arity.getAri().playbacks();
	}

	protected ActionRecordings recordings() {
		if (arity == null || arity.getAri() == null)
				throw new InvalidCallStateException();
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
		return ARItyException.ariRestExceptionMapper(ariError);
	}

	protected static Throwable unwrapCompletionError(Throwable error) {
		while (error instanceof CompletionException) {
			Throwable cause = error.getCause();
			if (cause == null)
				return error;
			error = cause;
		}
		return error;
	}
}

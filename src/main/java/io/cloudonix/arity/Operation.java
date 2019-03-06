package io.cloudonix.arity;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.future.helper.FutureHelper;

/**
 * A general class that represents an Asterisk operation
 * 
 * @author naamag
 *
 */
public abstract class Operation {
	private static final long RETRY_TIME = 1;
	private static final int RETRIES = 5;
	private String channelId;
	private ARIty arity;
	private ARI ari;

	/**
	 * Constructor
	 * 
	 * @param channelId id of the channel or bridge
	 * @param arity     instance of ARIty
	 * @param ari       ARI instance
	 */
	public Operation(String channelId, ARIty arity, ARI ari) {
		this.channelId = channelId;
		this.arity = arity;
		this.ari = ari;
	}

	/**
	 * get ARI instance
	 * 
	 * @return
	 */
	public ARI getAri() {
		return ari;
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
	 * the method receives an operation , creates a CompletableFuture and an
	 * AriCallback, and execute the operation on AriCallback
	 * 
	 * @param op the operation that we want to execute
	 * @return
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
				Exception wrap = new Exception("ARI operation failed: " + e.getMessage(), e);
				wrap.setStackTrace(caller);
				cf.completeExceptionally(wrap);
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

	/**
	 * retry to execute ARI operation few times
	 * 
	 * @param op the ARI operation to execute
	 * 
	 * @return
	 */
	public static <V> CompletableFuture<V> retryOperation(Consumer<AriCallback<V>> op) {
		CompletableFuture<V> f = new CompletableFuture<V>();
		CompletableFuture<Void>tempFuture = CompletableFuture.completedFuture(null);
		Timer timer = new Timer("Timer");
		AtomicBoolean success = new AtomicBoolean(false);
		AtomicReference<Throwable> ex =new AtomicReference<>();

		for (int i = RETRIES; i > 0 && !success.get(); i--) {
			tempFuture = tempFuture.thenCompose(v->{
				return toFuture(op).thenAccept(v2 -> {
					f.complete(v2);
					success.set(true);
				}).exceptionally(t -> {
					ex.set(t);
					return null;
				}).thenCompose(v2->{
					if(success.get())
						return FutureHelper.completedFuture();
					CompletableFuture<Void>timerFuture = new CompletableFuture<>();
					TimerTask task = new TimerTask() {
						@Override
						public void run() {
							timerFuture.complete(null);
						}
					};
					timer.schedule(task, TimeUnit.SECONDS.toMillis(RETRY_TIME));
					return timerFuture;
				});
			});
		}
		if(Objects.nonNull(ex.get()))
			f.completeExceptionally(ex.get());
		return f;
	}
}

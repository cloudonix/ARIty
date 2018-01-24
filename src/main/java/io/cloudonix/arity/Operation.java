package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;

public abstract class Operation {

	private String channelID;
	private ARIty aRIty;
	private ARI ari;

	public Operation(String chanId, ARIty s, ARI a) {
		channelID = chanId;
		aRIty = s;
		ari = a;
	}

	public ARI getAri() {
		return ari;
	}

	public String getChanneLID() {
		return channelID;
	}

	public ARIty getService() {
		return aRIty;
	}

	abstract CompletableFuture<? extends Operation> run();

	protected <V> CompletableFuture<V> toFuture(Consumer<AriCallback<V>> op) {
		CompletableFuture<V> cf = new CompletableFuture<V>();
		AriCallback<V> ariCallback = new AriCallback<V>() {

			@Override
			public void onSuccess(V result) {
				cf.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				cf.completeExceptionally(e);
			}
		};
		
		op.accept(ariCallback);
		return cf;
		
	}

	public static <V> CompletableFuture<V> completedExceptionally(Throwable th) {
		
		CompletableFuture<V> compExceptionaly = new CompletableFuture<V>();
		 compExceptionaly.completeExceptionally(th);
		 return compExceptionaly;
	}

}

package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;

/**
 * A general class that represents an operation
 * @author naamag
 *
 */
public abstract class Operation {

	private String channelId;
	private ARIty arity;
	private ARI ari;

	/**
	 * Constructor
	 * @param id id of the channel or bridge
	 * @param arity
	 * @param ari
	 */
	public Operation(String channelId, ARIty arity, ARI ari) {
		this.channelId = channelId;
		this.arity = arity;
		this.ari = ari;
	}

	/**
	 * get ari
	 * @return
	 */
	public ARI getAri() {
		return ari;
	}
	
	/**
	 * get id of the channel
	 * @return
	 */
	public String getChannelId() {
		return channelId;
	}
	
	/**
	 * get arity
	 * @return
	 */
	public ARIty getArity() {
		return arity;
	}

	public abstract CompletableFuture<? extends Operation> run();
	
	/**
	 * the method receives an operation , creates a CompletableFuture and an AriCallback,
	 * and execute the operation on AriCallback
	 * @param op the operation that we want to execute
	 * @return
	 */
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
}

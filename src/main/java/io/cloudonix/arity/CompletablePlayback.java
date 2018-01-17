package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.PlaybackException;
import ch.loway.oss.ari4java.ARI;

public class CompletablePlayback extends CompletableFuture<Playback> {

	private ARI ari;
	private CompletableFuture<Playback> compFuturePlayback;
	private Executor executor;

	public CompletablePlayback(ARI ari) {
		super();
		this.ari = ari;
		compFuturePlayback = new CompletableFuture<>();
		executor = null;
	}
	
	public CompletablePlayback(ARI ari, CompletableFuture<Playback> comFut) {
		super();
		this.ari = ari;
		compFuturePlayback = comFut;
		executor = null;
	}

	@Override
	public boolean complete(Playback playback) {

		try {
			ari.playbacks().stop(playback.getId());
		} catch (RestException e) {
			this.completeExceptionally(new PlaybackException(playback.getMedia_uri(), e));
		}

		return super.complete(playback);
	}

	public CompletablePlayback thenComposePlayback(Function<Playback, CompletableFuture<Playback>> fn) {
		return new CompletablePlayback(ari, compFuturePlayback.thenCompose(fn));
//		CompletableFuture<Playback> playbackFuture = new CompletableFuture<>();
//		playbackFuture = super.thenCompose(fn);
//		CompletablePlayback compPlayback = (CompletablePlayback) playbackFuture;
//		return compPlayback;
		// return (CompletablePlayback)super.thenCompose(fn);
	}
	
	/*@Override
	public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
		return compFuturePlayback.thenCompose(fn);
	}*/

	public void setExecutor(Executor ex) {
		executor = ex;
	}

	/*@Override
	public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
		return handler(compFuturePlayback.thenCompose(fn, executor));
	}*/
	
	/*private static <Playback> CompletableFuture<Playback> handler(CompletableFuture<Playback> f, Executor e) {
        f.whenComplete((v,t)-> {
            if(t!=null) compFuturePlayback.completeExceptionally(t); 
            else compFuturePlayback.complete(v);
        });
        return compFuturePlayback;
    }*/
}

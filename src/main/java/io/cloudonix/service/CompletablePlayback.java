package io.cloudonix.service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.service.errors.PlaybackException;
import ch.loway.oss.ari4java.ARI;

public class CompletablePlayback extends CompletableFuture<Playback>{
	
	private ARI ari;
	
	public CompletablePlayback (ARI ari) {
		super();
		this.ari = ari;
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
	
	public CompletablePlayback thenComposePlayback(Function<Playback, CompletableFuture<Playback>> fn)
	{
		return (CompletablePlayback) super.thenCompose(fn);
	}
	

}

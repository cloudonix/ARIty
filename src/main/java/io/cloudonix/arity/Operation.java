package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;

import ch.loway.oss.ari4java.ARI;

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

}

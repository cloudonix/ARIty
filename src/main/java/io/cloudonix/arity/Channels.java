package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;

import ch.loway.oss.ari4java.generated.models.Channel;
import io.cloudonix.arity.models.AsteriskChannel;

public class Channels {

	private ARIty arity;

	public Channels(ARIty arity) {
		this.arity = arity;
	}

	public CompletableFuture<AsteriskChannel> create(String endpoint, String channelId) {
		return Operation.<Channel>retry(cb -> arity.getAri().channels().create(endpoint, arity.getAppName())
				.setAppArgs("").setChannelId(channelId).execute(cb))
				.thenApply(c -> new AsteriskChannel(arity, c));
	}

	public CompletableFuture<Channel> hangup(String channelId) {
		return Operation.<Channel>retry(cb -> arity.getAri().channels().hangup(channelId));
	}

}

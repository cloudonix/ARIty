package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;

import ch.loway.oss.ari4java.generated.models.Channel;
import io.cloudonix.arity.models.AsteriskChannel;
import io.cloudonix.arity.models.AsteriskChannel.HangupReasons;

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

	public CompletableFuture<Void> hangup(String channelId) {
		return hangup(channelId, null);
	}

	public CompletableFuture<Void> hangup(String channelId, HangupReasons reason) {
		return Operation.<Void>retry(cb -> arity.getAri().channels().hangup(channelId)
					.setReason(reason != null ? reason.toString() : null).execute(cb));
	}

}

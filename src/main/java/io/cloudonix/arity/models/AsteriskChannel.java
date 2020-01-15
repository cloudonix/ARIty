package io.cloudonix.arity.models;

import java.util.concurrent.CompletableFuture;

import ch.loway.oss.ari4java.generated.models.Channel;
import io.cloudonix.arity.ARIty;

public class AsteriskChannel {

	private ARIty arity;
	private Channel channel;

	public enum HangupReasons {
		NORMAL("normal"),
		BUSY("busy"),
		CONGESTION("congestion"),
		NO_ANSWER("no_answer");

		private String reason;

		HangupReasons(String reason) {
			this.reason = reason;
		}

		public String toString() {
			return reason;
		}
	}

	public AsteriskChannel(ARIty arity, Channel channel) {
		this.arity = arity;
		this.channel = channel;
	}

	public CompletableFuture<AsteriskChannel> hangup() {
		return hangup(HangupReasons.NORMAL);
	}

	private CompletableFuture<AsteriskChannel> hangup(HangupReasons reason) {
		return arity.channels().hangup(channel.getId(), reason).thenApply(v -> this);
	}

}

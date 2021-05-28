package io.cloudonix.arity;

import java.net.InetSocketAddress;
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

	/* External Media Channels */
	
	public CompletableFuture<AsteriskChannel> externalMediaAudioSocket(String uuid, InetSocketAddress serverAddr) {
		String sockaddr = serverAddr.getAddress().getHostAddress() + ":" + serverAddr.getPort();
		CompletableFuture<CallState> waitForStart = arity.registerApplicationStartHandler(uuid);
		return Operation.<Channel>retry(cb -> arity.getAri().channels().externalMedia(arity.getAppName(), sockaddr, "slin")
				.setChannelId(uuid).setData(uuid).setEncapsulation("audiosocket").setTransport("tcp").execute(cb))
				.thenCompose(v -> waitForStart).thenApply(cs -> new AsteriskChannel(arity, cs.getChannel()));
	}

	public CompletableFuture<AsteriskChannel> externalMediaRTP(String uuid, InetSocketAddress serverAddr) {
		String sockaddr = serverAddr.getAddress().getHostAddress() + ":" + serverAddr.getPort();
		CompletableFuture<CallState> waitForStart = arity.registerApplicationStartHandler(uuid);
		return Operation.<Channel>retry(cb -> arity.getAri().channels().externalMedia(arity.getAppName(), sockaddr, "slin")
				.setChannelId(uuid).setData(uuid).setEncapsulation("rtp").setTransport("udp").execute(cb))
				.thenCompose(v -> waitForStart).thenApply(cs -> new AsteriskChannel(arity, cs.getChannel()));
	}


}

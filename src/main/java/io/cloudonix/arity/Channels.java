package io.cloudonix.arity;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.loway.oss.ari4java.generated.actions.ActionChannels;
import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.dial.ChannelNotFoundException;
import io.cloudonix.arity.models.AsteriskChannel;
import io.cloudonix.arity.models.AsteriskChannel.HangupReasons;

public class Channels {

	private ARIty arity;
	private ActionChannels api;

	@SuppressWarnings("deprecation")
	public Channels(ARIty arity) {
		this.arity = arity;
		this.api = arity.getAri().channels();
	}
	
	public static enum LocalChannelOptions {
		/** Prevent Asterisk from optimizing the local channel away */
		NoRelease("n"),
		/** Enable jitter buffer on the application side of the local channel */
		AppJitterBuffer("j"),
		/** Forward the MoH request to the destination native channel instead of playing to the local channel */
		ForwardMusicOnHold("m");
		
		String flag;
		private LocalChannelOptions(String flag) {
			this.flag = flag;
		}
	}
	
	public CompletableFuture<AsteriskChannel> createLocal(String name, String channelId, LocalChannelOptions... options) {
		return createLocal(name, "default", channelId, options);
	}
	
	public CompletableFuture<AsteriskChannel> createLocal(String name, String context, String channelId, 
			LocalChannelOptions... options) {
		var addr = String.format("Local/%1$s@%2$s", name, context);
		if (options.length > 0)
			addr += "/" + Stream.of(options).map(o -> o.flag).collect(Collectors.joining());
		return create(addr, channelId);
	}

	public CompletableFuture<AsteriskChannel> create(String endpoint, String channelId) {
		return Operation.<Channel>retry(cb -> api.create(endpoint, arity.getAppName())
				.setAppArgs("").setChannelId(channelId).execute(cb))
				.thenApply(c -> new AsteriskChannel(arity, c));
	}

	public CompletableFuture<Void> hangup(String channelId) {
		return hangup(channelId, null);
	}

	public CompletableFuture<Void> hangup(String channelId, HangupReasons reason) {
		return Operation.<Void>retry(cb -> api.hangup(channelId)
					.setReason(reason != null ? reason.toString() : null).execute(cb), Channels::mapChannelExceptions);
	}

	/* External Media Channels */
	
	public CompletableFuture<AsteriskChannel> externalMediaAudioSocket(String uuid, InetSocketAddress serverAddr) {
		String sockaddr = serverAddr.getAddress().getHostAddress() + ":" + serverAddr.getPort();
		CompletableFuture<CallState> waitForStart = arity.registerApplicationStartHandler(uuid);
		return Operation.<Channel>retry(cb -> api.externalMedia(arity.getAppName(), sockaddr, "slin")
				.setChannelId(uuid).setData(uuid).setEncapsulation("audiosocket").setTransport("tcp").execute(cb))
				.thenCompose(v -> waitForStart).thenApply(cs -> new AsteriskChannel(arity, cs.getChannel()));
	}

	public CompletableFuture<AsteriskChannel> externalMediaRTP(String uuid, InetSocketAddress serverAddr) {
		String sockaddr = serverAddr.getAddress().getHostAddress() + ":" + serverAddr.getPort();
		CompletableFuture<CallState> waitForStart = arity.registerApplicationStartHandler(uuid);
		return Operation.<Channel>retry(cb -> api.externalMedia(arity.getAppName(), sockaddr, "slin")
				.setChannelId(uuid).setData(uuid).setEncapsulation("rtp").setTransport("udp").execute(cb))
				.thenCompose(v -> waitForStart).thenApply(cs -> new AsteriskChannel(arity, cs.getChannel()));
	}

	private static Exception mapChannelExceptions(Throwable ariException) {
		if (ariException instanceof RestException)
			switch (((RestException)ariException).getCode()) {
			case 404: return new ChannelNotFoundException(ariException);
			}
		return null;
	}
}

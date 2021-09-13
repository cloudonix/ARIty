package io.cloudonix.arity;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
	
	/**
	 * Create a local channel with the specified options.
	 * The local channel will be created with random UUIDs for the channel IDs and will be registered to the current
	 * application. The endpoint for the new channel will be set to "<code>Local/<ARIty application name>@default</code>"
	 * @param options a set of local channel options
	 * @return a promise that will resolve with a new {@link AsteriskChannel} object that represents the new channel
	 */
	public CompletableFuture<AsteriskChannel> createLocal(LocalChannelOptions... options) {
		return createLocal(arity.getAppName(), "default", UUID.randomUUID().toString(), options);
	}
	
	/**
	 * Create a local channel with the specified ID and options.
	 * The local channel will be created with the specified channel ID for the first channel and a random UUID for the
	 * "other channel" id and will be registered to the current application.
	 * The endpoint for the new channel will be set to "<code>Local/<ARIty application name>@default</code>"
	 * @param channelId channel ID for the first channel - the one that will be put directly into stasis 
	 * @param options a set of local channel options
	 * @return a promise that will resolve with a new {@link AsteriskChannel} object that represents the new channel
	 */
	public CompletableFuture<AsteriskChannel> createLocal(String channelId, LocalChannelOptions... options) {
		return createLocal(arity.getAppName(), channelId, options);
	}

	/**
	 * Create a local channel with the specified ID and options, connecting to the specified dial plan extension and context.
	 * The local channel will be created with the specified channel ID for the first channel and a random UUID for the
	 * "other channel" id and will be registered to the current application.
	 * The endpoint for the new channel will be set according to the specified extenstion and context
	 * @param extension dial plan extension for the new local channel set
	 * @param context dial plan context for the new local channel set
	 * @param channelId channel ID for the first channel - the one that will be put directly into stasis 
	 * @param options a set of local channel options
	 * @return a promise that will resolve with a new {@link AsteriskChannel} object that represents the new channel
	 */
	public CompletableFuture<AsteriskChannel> createLocal(String name, String channelId, LocalChannelOptions... options) {
		return createLocal(name, channelId, UUID.randomUUID().toString(), options);
	}
	
	/**
	 * Create a local channel with the specified IDs and options, connecting to the specified dial plan extension and context.
	 * The local channel will be created with the specified channel IDs for both the first channel (that will be put into stasis)
	 * and the "other channel", and will be registered to the current application.
	 * The endpoint for the new channel will be set according to the specified extenstion and context
	 * @param extension dial plan extension for the new local channel set
	 * @param context dial plan context for the new local channel set
	 * @param channelId channel ID for the first channel - the one that will be put directly into stasis
	 * @param otherChannelId channel ID for the other channel, that will not go into stasis 
	 * @param options a set of local channel options
	 * @return a promise that will resolve with a new {@link AsteriskChannel} object that represents the new channel
	 */
	public CompletableFuture<AsteriskChannel> createLocal(String name, String channelId, String otherChannelId, LocalChannelOptions... options) {
		var addr = new StringBuffer("Local/").append(name);
		if (options.length > 0) {
			addr.append('/');
			Stream.of(options).map(o -> o.flag).forEach(f -> addr.append(f));
		}
		return create(addr.toString(), channelId, otherChannelId);
	}

	public CompletableFuture<AsteriskChannel> create(String endpoint, String channelId) {
		return create(endpoint, channelId, null);
	}
	
	public CompletableFuture<AsteriskChannel> create(String endpoint, String channelId, String otherChannelId) {
		return Operation.<Channel>retry(cb -> {
			var req = api.create(endpoint, arity.getAppName()).setAppArgs("").setChannelId(channelId);
			if (otherChannelId != null)
				req.setOtherChannelId(otherChannelId);
			req.execute(cb);
		})
				.thenApply(c -> new AsteriskChannel(arity, c, otherChannelId));
	}

	public CompletableFuture<Void> answer(String channelId) {
		return Operation.<Void>retry(cb -> api.answer(channelId).execute(cb), Channels::mapChannelExceptions);
	}

	public CompletableFuture<Void> dial(String channelId, String caller, int timeout) {
		return Operation.<Void>retry(cb -> api.dial(channelId).setCaller(caller).setTimeout(timeout).execute(cb), Channels::mapChannelExceptions);
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

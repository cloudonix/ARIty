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
	 * 
	 * The local channel will be created with random UUIDs for the channel IDs and will connect to the dial plan
	 * default context with ARIty application name as the extension.
	 * 
	 * Please note that you must call {@link AsteriskChannel#dial()} on the resolved channel to start the dial plan.
	 * 
	 * @param options a set of local channel options
	 * @return a promise that will resolve with a new {@link AsteriskChannel} object that represents the new channel
	 */
	public CompletableFuture<AsteriskChannel> createLocal(LocalChannelOptions... options) {
		return createLocal(arity.getAppName(), null, UUID.randomUUID().toString(), options);
	}
	
	/**
	 * Create a local channel with the specified ID and options.
	 * 
	 * The local channel will be created with the specified channel ID for the first channel and a random UUID for the
	 * "other channel" and and will connect to the dial plan default context with ARIty application name as the extension.
	 * 
	 * Please note that you must call {@link AsteriskChannel#dial()} on the resolved channel to start the dial plan.
	 * 
	 * @param channelId channel ID for the first channel - set to <code>null</code> to have
	 *   Asterisk generate an arbitrary id
	 * @param options a set of local channel options
	 * @return a promise that will resolve with a new {@link AsteriskChannel} for the first channel
	 */
	public CompletableFuture<AsteriskChannel> createLocal(String channelId, LocalChannelOptions... options) {
		return createLocal(arity.getAppName(), null, channelId, options);
	}

	/**
	 * Create a local channel with the specified ID and options, connecting to the specified dial plan extension and context.
	 * 
	 * The local channel will be created with the specified channel ID for the first channel and a random UUID for the
	 * "other channel" and will connect to the specified dial plan context and extension.
	 * 
	 * Please note that you must call {@link AsteriskChannel#dial()} on the resolved channel to start the dial plan.
	 * 
	 * @param extension dial plan extension to run for the other channel
	 * @param context dial plan context to run for the other channel - this may be set to <code>null</code> to use the
	 *   default context configured in Asterisk
	 * @param channelId channel ID for the first channel - set to <code>null</code> to have Asterisk generate an arbitrary id 
	 * @param options a set of local channel options
	 * @return a promise that will resolve with a new {@link AsteriskChannel} for the first channel.
	 */
	public CompletableFuture<AsteriskChannel> createLocal(String extension, String context, String channelId, LocalChannelOptions... options) {
		return createLocal(extension, context, channelId, UUID.randomUUID().toString(), options);
	}
	
	/**
	 * Create a local channel with the specified IDs and options, connecting to the specified dial plan extension and context.
	 * 
	 * The local channel will be created with the specified channel IDs for the first channel and the "other channel",
	 * and and will connect to the specified dial plan context and extension.
	 * 
	 * Please note that you must call {@link AsteriskChannel#dial()} on the resolved channel to start the dial plan.
	 * 
	 * @param extension dial plan extension to run for the other channel
	 * @param context dial plan context to run for the other channel - this may be set to <code>null</code> to use the
	 *   default context configured in Asterisk
	 * @param channelId channel ID for the first channel - set to <code>null</code> to have Asterisk generate an arbitrary id
	 * @param otherChannelId channel ID for the other channel that will run in the dial plan - set to <code>null</code> to have
	 *   Asterisk generate an arbitrary id
	 * @param options a set of local channel options
	 * @return a promise that will resolve with a new {@link AsteriskChannel} for the first channel
	 */
	public CompletableFuture<AsteriskChannel> createLocal(String extension, String context, String channelId, String otherChannelId, LocalChannelOptions... options) {
		var addr = new StringBuffer("Local/").append(extension);
		if (context != null)
			addr.append('@').append(context);
		if (options.length > 0) {
			addr.append('/');
			Stream.of(options).map(o -> o.flag).forEach(f -> addr.append(f));
		}
		return create(addr.toString(), channelId, otherChannelId);
	}

	/**
	 * Create a new channel with the specified endpoint and channel Id
	 * @param endpoint The endpoint this channel will dial to, such as a SIP address
	 * @param channelId the new channel's id - set to <code>null</code> to have Asterisk generate an arbitrary id
	 * @return a promise that will resolve with the new {@link AsteriskChannel} 
	 */
	public CompletableFuture<AsteriskChannel> create(String endpoint, String channelId) {
		return create(endpoint, channelId, null);
	}
	
	private CompletableFuture<AsteriskChannel> create(String endpoint, String channelId, String otherChannelId) {
		return Operation.<Channel>retry(cb -> {
			var req = api.create(endpoint, arity.getAppName()).setAppArgs("").setChannelId(channelId);
			if (otherChannelId != null)
				req.setOtherChannelId(otherChannelId);
			req.execute(cb);
		})
				.thenApply(c -> new AsteriskChannel(arity, new CallState(c, arity), otherChannelId));
	}

	/**
	 * Answer the specified channel.
	 * @param channelId channel id to answer
	 * @return a promise that will resolve when the channel was answered
	 */
	public CompletableFuture<Void> answer(String channelId) {
		return Operation.<Void>retry(cb -> api.answer(channelId).execute(cb), Channels::mapChannelExceptions);
	}

	/**
	 * Dial the specified channel
	 * @param channelId channel id to dial
	 * @return a promise that will resolve when the channel was dialed
	 */
	public CompletableFuture<Void> dial(String channelId) {
		return Operation.<Void>retry(cb -> api.dial(channelId).execute(cb), Channels::mapChannelExceptions);
	}

	/**
	 * Dial the specified channel
	 * @param channelId channel id to dial
	 * @param caller the caller id to set for the dial
	 * @return a promise that will resolve when the channel was dialed
	 */
	public CompletableFuture<Void> dial(String channelId, String caller) {
		return Operation.<Void>retry(cb -> api.dial(channelId).setCaller(caller).execute(cb), Channels::mapChannelExceptions);
	}

	/**
	 * Dial the specified channel
	 * @param channelId channel id to dial
	 * @param caller the caller id to set for the dial
	 * @param timeout the dial timeout (in seconds) to set
	 * @return a promise that will resolve when the channel was dialed
	 */
	public CompletableFuture<Void> dial(String channelId, String caller, int timeout) {
		return Operation.<Void>retry(cb -> api.dial(channelId).setCaller(caller).setTimeout(timeout).execute(cb), Channels::mapChannelExceptions);
	}

	/**
	 * Hangup the specified channel with the default reason
	 * @param channelId channel id to hangup
	 * @return a promise that will resolve when the channel was hanged up
	 */
	public CompletableFuture<Void> hangup(String channelId) {
		return hangup(channelId, null);
	}

	/**
	 * Hangup the specified channel with the specified reason
	 * @param channelId channel id to hangup
	 * @param reason the reason to specify for the hangup
	 * @return a promise that will resolve when the channel was hanged up
	 */
	public CompletableFuture<Void> hangup(String channelId, HangupReasons reason) {
		return Operation.<Void>retry(cb -> api.hangup(channelId)
					.setReason(reason != null ? reason.toString() : null).execute(cb), Channels::mapChannelExceptions);
	}

	/* External Media Channels */
	
	/**
	 * Create a new channel that streams media to an external audio socket
	 * (see the <a href="https://wiki.asterisk.org/wiki/display/AST/AudioSocket">Asterisk Audio Socket protocol</a>) 
	 * @param channelId channel id for the new channel
	 * @param serverAddr external audio socket TCP socket address
	 * @return a promise that will resolve with the new channel when the audio socket protocol starts
	 */
	public CompletableFuture<AsteriskChannel> externalMediaAudioSocket(String channelId, InetSocketAddress serverAddr) {
		String sockaddr = serverAddr.getAddress().getHostAddress() + ":" + serverAddr.getPort();
		CompletableFuture<CallState> waitForStart = arity.waitForNewCallState(channelId);
		return Operation.<Channel>retry(cb -> api.externalMedia(arity.getAppName(), sockaddr, "slin")
				.setChannelId(channelId).setData(channelId).setEncapsulation("audiosocket").setTransport("tcp").execute(cb))
				.thenCompose(v -> waitForStart).thenApply(cs -> new AsteriskChannel(arity, cs));
	}

	/**
	 * Create a new channel that streams media to an external RTP socket
	 * @param channelId channel id for the new channel
	 * @param serverAddr external media RTP UDP socket address
	 * @return a promise that will resolve with the new channel when the RTP stream starts
	 */
	public CompletableFuture<AsteriskChannel> externalMediaRTP(String channelId, InetSocketAddress serverAddr) {
		String sockaddr = serverAddr.getAddress().getHostAddress() + ":" + serverAddr.getPort();
		CompletableFuture<CallState> waitForStart = arity.waitForNewCallState(channelId);
		return Operation.<Channel>retry(cb -> api.externalMedia(arity.getAppName(), sockaddr, "slin")
				.setChannelId(channelId).setData(channelId).setEncapsulation("rtp").setTransport("udp").execute(cb))
				.thenCompose(v -> waitForStart).thenApply(cs -> new AsteriskChannel(arity, cs));
	}

	private static Exception mapChannelExceptions(Throwable ariException) {
		if (ariException instanceof RestException)
			switch (((RestException)ariException).getCode()) {
			case 404: return new ChannelNotFoundException(ariException);
			}
		return null;
	}
}

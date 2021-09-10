package io.cloudonix.arity.models;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import ch.loway.oss.ari4java.generated.actions.ActionChannels;
import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallState;
import io.cloudonix.arity.Operation;

public class AsteriskChannel {

	private ARIty arity;
	private Channel channel;
	private ActionChannels api;
	private String localOtherId;

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
		this(arity, channel, null);
	}
	
	@SuppressWarnings("deprecation")
	public AsteriskChannel(ARIty arity, Channel channel, String localOtherId) {
		this.arity = arity;
		this.channel = channel;
		this.localOtherId = localOtherId;
		this.api = arity.getAri().channels();
	}
	
	/**
	 * Hangup the channel with a "normal" reason
	 * @return a promise that resolve to itself or rejects if the API encountered errors
	 */
	public CompletableFuture<AsteriskChannel> hangup() {
		return hangup(HangupReasons.NORMAL);
	}

	/**
	 * Hangup the channel
	 * @param reason reason to set as the hangup reason
	 * @return a promise that resolve to itself or rejects if the API encountered errors
	 */
	public CompletableFuture<AsteriskChannel> hangup(HangupReasons reason) {
		return Operation.<Void>retry(cb -> api.hangup(channel.getId()).setReason(reason.reason).execute(cb), this::mapExceptions).thenApply(v -> this);
	}
	
	/**
	 * Alias to {@link #hangup()}
	 * @return a promise that resolve to itself or rejects if the API encountered errors
	 */
	public CompletableFuture<AsteriskChannel> delete() {
		return hangup(HangupReasons.NORMAL);
	}

	public String getId() {
		return channel.getId();
	}
	
	/**
	 * For a local channel, return the channel ID for "the other side" of the local channel
	 * @return The channel ID for the other side of a local channel, if the channel is a Local channel where an "other channe"
	 *   ID was specified, null otherwise;
	 */
	public String getOtherId() {
		return localOtherId;
	}
	
	public boolean isLocal() {
		return channel.getName().startsWith("Local/");
	}

	/**
	 * The extension that put this channel in stasis
	 * @return the current dialplan extension or null if no dial plan is found
	 */
	public String getExtension() {
		return (channel != null && channel.getDialplan() != null) ? channel.getDialplan().getExten() : null;
	}
	
	/**
	 * Return account code of the channel (information about the channel)
	 * @return channel account code or null if no channel is found
	 */
	public String getAccountCode() {
		return channel != null ? channel.getAccountcode() : null;
	}
	
	/**
	 * Retrieve the caller identifier number for the caller that ran this stasis application
	 * @return the caller id number or null if no channel is found
	 */
	public String getCallerIdNumber() {
		return channel != null && channel.getCaller() != null ? channel.getCaller().getNumber() : null;
	}

	/**
	 * Retrieve the name of the channel (for example: SIP/myapp-000001)
	 * @return the name of the channel or null if no channel is found
	 */
	public String getChannelName() {
		return channel != null ? channel.getName() : null;
	}

	/**
	 * Retrieve the channel state when the stasis application launched
	 * @return The channel state or null if no channel is found
	 */
	public String getChannelState() {
		return channel != null ? channel.getState() : null;
	}

	/**
	 * Retrieve the channel creation time
	 * @return the channel creation time or null if no channel is found
	 */
	public Date getChannelCreationTime() {
		return channel != null ? channel.getCreationtime() : null;
	}

	/**
	 * Retrieve the dialplan context (for example: ari-context) that ran this stasis application
	 * @return contet name or null if no channel is found
	 */
	public String getDialplanContext() {
		return channel != null && channel.getDialplan() != null ? channel.getDialplan().getContext() : null;
	}

	/**
	 * Retrieve the current priority of the extension that ran this stasis application
	 * @return priority number
	 */
	public long getPriority() {
		return channel != null && channel.getDialplan() != null ? channel.getDialplan().getPriority() : 0;
	}
	
	public String getLanguage() {
		return channel != null ? channel.getLanguage() : null;
	}

	/* Recording */
	
	public CompletableFuture<AsteriskRecording> record() {
		return record(b -> {});
	}

	public CompletableFuture<AsteriskRecording> record(boolean playBeep, int maxDuration, int maxSilence, String terminateOnDTMF) {
		return record(b -> b.withPlayBeep(playBeep).withMaxDuration(maxDuration).withMaxSilence(maxSilence).withTerminateOn(Objects.requireNonNull(terminateOnDTMF)));
	}
	
	public CompletableFuture<AsteriskRecording> record(Consumer<AsteriskRecording.Builder> withBuilder) {
		return Operation.<LiveRecording>retry(cb ->  AsteriskRecording.build(withBuilder).build(api.record(getId(), null, null), arity).execute(cb), this::mapExceptions)
				.thenApply(rec -> new AsteriskRecording(arity, rec));
	}
	
	private Exception mapExceptions(Throwable ariError) {
		switch (ariError.getMessage()) {
		case "Channel not in Stasis application": return new io.cloudonix.arity.errors.ChannelInInvalidState(ariError);
		}
		return null;
	}
	
	public interface Snoop {
		public enum Spy { none, both, out, in };
		public enum Whisper { none, both, out, in }
	}

	/**
	 * Create a snoop channel for this channel, capturing its StasisStart event and returning the active snoop channel.
	 * @return A promise that will resolve to the snoop channel when it has entered stasis 
	 */
	public CompletableFuture<AsteriskChannel> snoop(Snoop.Spy spy, Snoop.Whisper whisper) {
		String snoopId = UUID.randomUUID().toString();
		CompletableFuture<CallState> waitForStart = arity.registerApplicationStartHandler(snoopId);
		return Operation.<Channel>retry(cb -> api.snoopChannel(channel.getId(), arity.getAppName())
				.setSnoopId(snoopId).setSpy(spy.name()).setWhisper(whisper.name()).execute(cb), this::mapExceptions)
				.thenCompose(c -> waitForStart.thenApply(cs -> new AsteriskChannel(arity, c))); // ignore new call state for now
	}

	public CompletableFuture<AsteriskChannel> startSilence() {
		return Operation.<Void>retry(cb -> api.startSilence(channel.getId()).execute(cb), this::mapExceptions)
				.thenApply(v -> this);
	}

	public CompletableFuture<AsteriskChannel> stopSilence() {
		return Operation.<Void>retry(cb -> api.stopSilence(channel.getId()).execute(cb), this::mapExceptions)
				.thenApply(v -> this);
	}

	public CompletableFuture<AsteriskChannel> answer() {
		return Operation.<Void>retry(cb -> api.answer(channel.getId()).execute(cb), this::mapExceptions)
				.thenApply(v -> this);
	}

}

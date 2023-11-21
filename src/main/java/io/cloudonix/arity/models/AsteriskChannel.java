package io.cloudonix.arity.models;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import ch.loway.oss.ari4java.generated.actions.ActionChannels;
import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.ChannelStateChange;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallState;
import io.cloudonix.arity.Operation;
import io.cloudonix.arity.errors.ARItyException;
import io.cloudonix.arity.CallState.States;

public class AsteriskChannel {

	private ARIty arity;
	private CallState callState;
	private ActionChannels api;
	private String localOtherId;
	private String channelId;

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

	public enum Mute {
		NO(""), IN("in"), OUT("out"), BOTH("both");

		private String direction;

		Mute(String direction) {
			this.direction = direction;
		}
		
		public String value() {
			return direction;
		}
	}
	
	public AsteriskChannel(ARIty arity, CallState callState) {
		this(arity, callState, null);
	}
	
	@SuppressWarnings("deprecation")
	public AsteriskChannel(ARIty arity, CallState callState, String localOtherId) {
		this.arity = arity;
		this.callState = callState;
		this.localOtherId = localOtherId;
		this.api = arity.getAri().channels();
		this.channelId = callState.getChannelId();
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
		return Operation.<Void>retry(cb -> api.hangup(channelId).setReason(reason.reason).execute(cb), ARItyException::ariRestExceptionMapper).thenApply(v -> this);
	}
	
	/**
	 * Alias to {@link #hangup()}
	 * @return a promise that resolve to itself or rejects if the API encountered errors
	 */
	public CompletableFuture<AsteriskChannel> delete() {
		return hangup(HangupReasons.NORMAL);
	}

	public String getId() {
		return channelId;
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
		return getChannelName().startsWith("Local/");
	}

	/**
	 * The extension that put this channel in stasis
	 * @return the current dialplan extension or null if no dial plan is found
	 */
	public String getExtension() {
		return (callState.getChannel().getDialplan() != null) ? callState.getChannel().getDialplan().getExten() : null;
	}
	
	/**
	 * Return account code of the channel (information about the channel)
	 * @return channel account code or null if no channel is found
	 */
	public String getAccountCode() {
		return callState.getChannel().getAccountcode();
	}
	
	/**
	 * Retrieve the caller identifier number for the caller that ran this stasis application
	 * @return the caller id number or null if no channel is found
	 */
	public String getCallerIdNumber() {
		return callState.getChannel().getCaller() != null ? callState.getChannel().getCaller().getNumber() : null;
	}

	/**
	 * Retrieve the name of the channel (for example: SIP/myapp-000001)
	 * @return the name of the channel or null if no channel is found
	 */
	public String getChannelName() {
		return callState.getChannel().getName();
	}

	/**
	 * Retrieve the channel state when the stasis application launched
	 * @return The channel state or null if no channel is found
	 */
	public String getChannelState() {
		return callState.getChannel().getState();
	}

	/**
	 * Retrieve the channel creation time
	 * @return the channel creation time or null if no channel is found
	 */
	public Date getChannelCreationTime() {
		return callState.getChannel().getCreationtime();
	}

	/**
	 * Retrieve the dialplan context (for example: ari-context) that ran this stasis application
	 * @return contet name or null if no channel is found
	 */
	public String getDialplanContext() {
		return callState.getChannel().getDialplan() != null ? callState.getChannel().getDialplan().getContext() : null;
	}

	/**
	 * Retrieve the current priority of the extension that ran this stasis application
	 * @return priority number
	 */
	public long getPriority() {
		return callState.getChannel().getDialplan() != null ? callState.getChannel().getDialplan().getPriority() : 0;
	}
	
	public String getLanguage() {
		return callState.getChannel().getLanguage();
	}
	
	/* State Listeners */
	
	public void onStateChange(Consumer<States> eventHandler) {
		callState.registerEventHandler(ChannelStateChange.class,
				stateChange -> eventHandler.accept(States.find(stateChange.getChannel().getState())));
	}
	
	public void onRinging(Runnable action) {
		callState.registerStateHandler(States.Ring, action);
		callState.registerStateHandler(States.Ringing, action);
	}

	public void onConnect(Runnable action) {
		callState.registerStateHandler(States.Up, action);
	}

	public void onHangup(Runnable action) {
		callState.registerStateHandler(States.Hangup, action);
	}

	/* Recording */
	
	public CompletableFuture<AsteriskRecording> record() {
		return record(b -> {});
	}

	public CompletableFuture<AsteriskRecording> record(boolean playBeep, int maxDuration, int maxSilence, String terminateOnDTMF) {
		return record(b -> b.withPlayBeep(playBeep).withMaxDuration(maxDuration).withMaxSilence(maxSilence).withTerminateOn(Objects.requireNonNull(terminateOnDTMF)));
	}
	
	public CompletableFuture<AsteriskRecording> record(Consumer<AsteriskRecording.Builder> withBuilder) {
		return Operation.<LiveRecording>retry(cb ->  AsteriskRecording.build(withBuilder).build(api.record(getId(), null, null), arity).execute(cb), ARItyException::ariRestExceptionMapper)
				.thenApply(rec -> new AsteriskRecording(arity, rec));
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
		CompletableFuture<CallState> waitForStart = arity.waitForNewCallState(snoopId);
		return Operation.<Channel>retry(cb -> api.snoopChannel(channelId, arity.getAppName())
				.setSnoopId(snoopId).setSpy(spy.name()).setWhisper(whisper.name()).execute(cb), ARItyException::ariRestExceptionMapper)
				.thenCompose(c -> waitForStart.thenApply(cs -> new AsteriskChannel(arity, cs))); // ignore new call state for now
	}

	public CompletableFuture<AsteriskChannel> startSilence() {
		return Operation.<Void>retry(cb -> api.startSilence(channelId).execute(cb), ARItyException::ariRestExceptionMapper)
				.thenApply(v -> this);
	}

	public CompletableFuture<AsteriskChannel> stopSilence() {
		return Operation.<Void>retry(cb -> api.stopSilence(channelId).execute(cb), ARItyException::ariRestExceptionMapper)
				.thenApply(v -> this);
	}

	public CompletableFuture<AsteriskChannel> answer() {
		return Operation.<Void>retry(cb -> api.answer(channelId).execute(cb), ARItyException::ariRestExceptionMapper)
				.thenApply(v -> this);
	}

	public CompletableFuture<AsteriskChannel> dial() {
		return Operation.<Void>retry(cb -> api.dial(channelId)
				.execute(cb), ARItyException::ariRestExceptionMapper)
				.thenApply(v -> this);
	}

	public CompletableFuture<AsteriskChannel> dial(String caller) {
		return Operation.<Void>retry(cb -> api.dial(channelId)
				.setCaller(caller)
				.execute(cb), ARItyException::ariRestExceptionMapper)
				.thenApply(v -> this);
	}

	public CompletableFuture<AsteriskChannel> dial(String caller, int timeout) {
		return Operation.<Void>retry(cb -> api.dial(channelId)
				.setCaller(caller).setTimeout(timeout)
				.execute(cb), ARItyException::ariRestExceptionMapper)
				.thenApply(v -> this);
	}
	
}

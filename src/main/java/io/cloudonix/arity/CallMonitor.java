package io.cloudonix.arity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ChannelStateChange;

/**
 * The class monitors the activity of the call
 * 
 * @author naamag
 *
 */
public class CallMonitor {

	private String callerChannelId;
	private ARIty arity;
	private List<Runnable> onHangUp = Collections.emptyList();
	private boolean isActive = true;
	private boolean wasAnswered = false;

	public CallMonitor(ARIty arity, String callChannelId) {
		this.arity = arity;
		this.callerChannelId = callChannelId;
		monitorCallHangUp();
		monitorCallAnswered();
	}

	/**
	 * monitor hang up of the call event
	 */
	private void monitorCallHangUp() {
		arity.addFutureEvent(ChannelHangupRequest.class, callerChannelId, this::handleHangupCaller, true);
	}

	/**
	 * handle when a hang up of the channel occurs
	 * 
	 * @param hangup ChannelHangupRequest event
	 * @return
	 */
	private Boolean handleHangupCaller(ChannelHangupRequest hangup) {
		isActive = false;
		onHangUp.forEach(Runnable::run);
		return true;
	}

	/**
	 * monitor when the channel is answered
	 */
	private void monitorCallAnswered() {
		arity.addFutureEvent(ChannelStateChange.class, callerChannelId, this::handleAnswer, false);
	}

	/**
	 * handle when channel state changed
	 * 
	 * @param state channel state change event
	 * @return
	 */
	public Boolean handleAnswer(ChannelStateChange state) {
		if (!Objects.equals(state.getChannel().getState().toLowerCase(), "up"))
			return false;
		wasAnswered = true;
		return true;
	}

	/**
	 * register the handler which will be called if hang up event accrued
	 * 
	 * @param hangUpHandler
	 */
	public void registerHangUpHandler(Runnable hangUpHandler) {
		onHangUp.add(hangUpHandler);
	}

	/**
	 * true if the the call was not hanged up, false otherwise
	 * 
	 * @return
	 */
	public boolean isActive() {
		return isActive;
	}

	/**
	 * true if the call was answered, false otherwise
	 * 
	 * @return
	 */
	public boolean wasAnswered() {
		return wasAnswered;
	}

	/**
	 * wait for hang up event
	 * @return
	 */
	public CompletableFuture<Void> waitForHangup() {
		CompletableFuture<Void>future = new CompletableFuture<Void>();
		registerHangUpHandler(()->future.complete(null));
		return future;
	}
}

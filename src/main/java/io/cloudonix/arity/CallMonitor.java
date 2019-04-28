package io.cloudonix.arity;

import java.util.LinkedList;
import java.util.List;
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

	private String channelId;
	private List<Runnable> onHangUp = new LinkedList<Runnable>();
	private boolean isActive = true;
	private boolean wasAnswered = false;
	private SavedEvent<ChannelStateChange>channelStateChangedSE;

	public CallMonitor(ARIty arity, String callChannelId) {
		this.channelId = callChannelId;
		arity.addFutureOneTimeEvent(ChannelHangupRequest.class, channelId, this::handleHangupCaller);
		arity.addFutureEvent(ChannelStateChange.class, channelId, this::handleAnswer);
	}
	
	/**
	 * handle when a hang up of the channel occurs
	 * 
	 * @param hangup ChannelHangupRequest event
	 * @return
	 */
	private void handleHangupCaller(ChannelHangupRequest hangup) {
		isActive = false;
		onHangUp.forEach(Runnable::run);
		channelStateChangedSE.unregister(); // need also to unregister from channel event
	}

	/**
	 * handle when channel state changed
	 * 
	 * @param state channel state change event
	 * @return
	 */
	public void handleAnswer(ChannelStateChange state, SavedEvent<ChannelStateChange>se) {
		wasAnswered |= state.getChannel().getState().toLowerCase().equals("up");
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

	public String getCallerChannelId() {
		return channelId;
	}
}

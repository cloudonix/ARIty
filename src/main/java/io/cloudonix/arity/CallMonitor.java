package io.cloudonix.arity;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ChannelStateChange;

/**
 * The class monitors the activity of the call
 * 
 * @author naamag
 *
 */
public class CallMonitor {
	
	public static enum States {
		Down("Down"),
		Rsrvd("Rsrvd"),
		OffHook("OffHook"),
		Dialing("Dialing"),
		Ring("Ring"),
		Ringing("Ringing"),
		Up("Up"),
		Busy("Busy"),
		DialingOffhook("Dialing Offhook"),
		PreRing("Pre-ring"),
		Hangup(""), // not on official state, just for ease of use
		Unknown("Unknown");
		
		private String stateName;

		States(String stateName) {
			this.stateName = stateName;
		}
		
		public static States find(String state) {
			return Arrays.stream(values()).filter(s -> s.stateName.equalsIgnoreCase(state)).findFirst().orElse(Unknown);
		}
	}

	private String channelId;
	private boolean isActive = true;
	private boolean wasAnswered = false;
	private SavedEvent<ChannelStateChange>channelStateChangedSE;
	private ConcurrentHashMap<States, List<Runnable>> stateListeners = new ConcurrentHashMap<>();

	public CallMonitor(ARIty arity, String callChannelId) {
		this.channelId = callChannelId;
		arity.addFutureOneTimeEvent(ChannelHangupRequest.class, channelId, this::handleHangupCaller);
		channelStateChangedSE = arity.addFutureEvent(ChannelStateChange.class, channelId, this::handleStateChange);
	}
	
	private List<Runnable> getListeners(States state) {
		return stateListeners.computeIfAbsent(state, s -> new LinkedList<>());
	}
	
	/**
	 * Handle the hangup case which is not an actual state
	 * @param hangup ChannelHangupRequest event
	 */
	private void handleHangupCaller(ChannelHangupRequest hangup) {
		isActive = false;
		getListeners(States.Hangup).forEach(Runnable::run);
		channelStateChangedSE.unregister(); // need also to unregister from channel event
	}

	/**
	 * Handle the state change event
	 * @param state channel state change event
	 */
	private void handleStateChange(ChannelStateChange state, SavedEvent<ChannelStateChange>se) {
		States stat = States.find(state.getChannel().getState());
		wasAnswered |= stat == States.Up;
	}

	/**
	 * Register state event handlers
	 * @param state The state to listen for
	 * @param handler the handler to run when the state has changed to the specified state
	 */
	public void registerStateHandler(States state, Runnable handler) {
		getListeners(state).add(handler);
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
		registerStateHandler(States.Hangup, ()->future.complete(null));
		return future;
	}

	/**
	 * Retrieve the originator's channel ID
	 * @return channel id for the monitored call
	 */
	public String getCallerChannelId() {
		return channelId;
	}
}

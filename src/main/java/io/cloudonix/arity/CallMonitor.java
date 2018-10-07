package io.cloudonix.arity;

import java.util.Objects;

import ch.loway.oss.ari4java.generated.ChannelHangupRequest;

/**
 * The class monitors the activity of the call
 * 
 * @author naamag
 *
 */
public class CallMonitor {

	private String callerChannelId;
	private ARIty arity;
	private Runnable onHangUp;
	private boolean isActive = true;

	public CallMonitor(ARIty arity, String callChannelId) {
		this.arity = arity;
		this.callerChannelId = callChannelId;
	}

	/**
	 * Monitor hang up of the call event
	 */
	public void monitorCallHangUp() {
		arity.addFutureEvent(ChannelHangupRequest.class, callerChannelId, this::handleHangupCaller, true);
	}

	private Boolean handleHangupCaller(ChannelHangupRequest hangup) {
		isActive = false;
		if (Objects.nonNull(onHangUp))
			onHangUp.run();
		return true;
	}

	/**
	 * register the handler which will be called if hang up event accrued
	 * 
	 * @param hangUpHandler
	 */
	public void registerHangUpHandler(Runnable hangUpHandler) {
		onHangUp = hangUpHandler;
	}

	public boolean isActive() {
		return isActive;
	}
}

package io.cloudonix.arity;

import ch.loway.oss.ari4java.generated.ChannelHangupRequest;

/**
 * The class monitors the activity of the call
 * 
 * @author naamag
 *
 */
public class CallMointor {

	private String callerChannelId;
	private ARIty arity;
	private Runnable onHangUp;

	public CallMointor(ARIty arity, String callChannelId) {
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
		onHangUp.run();
		return true;
	}

	/**
	 * register the handler which will be called if hang up event accrued 
	 * @param hangUpHandler
	 */
	public void registerHangUpHandler(Runnable hangUpHandler) {
		onHangUp = hangUpHandler;
	}
}

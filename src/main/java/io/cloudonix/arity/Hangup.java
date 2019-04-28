package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * The class represents the Hang up operation (hangs up a call)
 * 
 * @author naamag
 *
 */

public class Hangup extends Operation {

	private final static Logger logger = Logger.getLogger(Hangup.class.getName());
	private String reason = "normal";

	/**
	 * Constructor
	 * 
	 * @param callController instance that represents a call
	 */
	public Hangup(CallController callController) {
		super(callController.getChannelId(), callController.getARIty());
	}

	/**
	 * The method hangs up a channel
	 * 
	 * @return
	 */
	public CompletableFuture<Hangup> run() {
		return Operation.<Void>retryOperation(cb->channels().hangup(getChannelId(),reason,cb))
				.thenApply(res->{
					logger.info("Channel with id: "+getChannelId()+" was hanged up");
					return this;
				});
	}

	/**
	 * get the reason for hang up
	 * 
	 * @return
	 */
	public String getReason() {
		return reason;
	}

	/**
	 * set the reason for why the call is hanged up. Allowed values: normal, busy,
	 * congestion, no_answer
	 * 
	 * @param reason
	 */
	public void setReason(String reason) {
		this.reason = reason;
	}
	
}

package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.HangUpException;

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
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
	}

	/**
	 * The method hangs up a channel
	 * 
	 * @return
	 */
	public CompletableFuture<Hangup> run() {
		CompletableFuture<Hangup> future = new CompletableFuture<Hangup>();
		getAri().channels().hangup(getChannelId(), reason, new AriCallback<Void>() {

			@Override
			public void onSuccess(Void result) {
				logger.info("Hanged up channel with id: " + getChannelId());
				future.complete(null);
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("Failed hang up channel with id: " + getChannelId() + " : " + e);
				future.completeExceptionally(new HangUpException(e));
			}
		});
		return future;
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

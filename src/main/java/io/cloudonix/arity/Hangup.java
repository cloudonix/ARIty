package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudonix.arity.errors.ChannelNotFoundException;
import io.cloudonix.arity.helpers.Futures;

/**
 * The class represents the Hang up operation (hangs up a call)
 *
 * @author naamag
 *
 */

public class Hangup extends Operation {

	private final static Logger logger = LoggerFactory.getLogger(Hangup.class);
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
		return this.<Void>retryOperation(cb->channels().hangup(getChannelId()).setReason(reason).execute(cb))
				.exceptionally(Futures.on(ChannelNotFoundException.class, e -> null))
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
	 * congestion, no_answer, timeout, rejected, unallocated, normal_unspecified,
	 * number_incomplete, codec_mismatch, interworking, failure, answered_elsewhere
	 * 
	 * See here https://wiki.asterisk.org/wiki/display/AST/Hangup+Cause+Mappings for a list of
	 * mapping between reason code and SIP status code
	 * 
	 * @param reason one of the above specified values
	 */
	public void setReason(String reason) {
		this.reason = reason;
	}

}

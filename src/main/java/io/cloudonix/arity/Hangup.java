package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;

/**
 * The class represents the Hang up operation (hangs up a call)
 * 
 * @author naamag
 *
 */
public class Hangup extends Operation {

	private final static Logger logger = Logger.getLogger(Hangup.class.getName());
	private String channelId = "";

	/**
	 * Constructor
	 * 
	 * @param callController
	 *            instance that represents a call
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
		String id = (Objects.equals(channelId, "")) ? getChannelId() : channelId;
		getAri().channels().hangup(id, "normal", new AriCallback<Void>() {

			@Override
			public void onSuccess(Void result) {
				logger.info("Hanged up channel with id: " + getChannelId());
				future.complete(null);
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("Failed hang up channel with id: " + getChannelId());
				future.complete(null);
			}
		});
		return future;
	}

	/**
	 * set the channel id when need to hang up another channel
	 * 
	 * @param channelId
	 *            the id of the channel to be hanged up
	 * 
	 */
	public void setChannelId(String channelId) {
		this.channelId = channelId;
	}
}

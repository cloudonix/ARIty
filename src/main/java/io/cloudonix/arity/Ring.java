package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.RingException;
import io.cloudonix.future.helper.FutureHelper;


public class Ring  extends Operation{
	
	private final static Logger logger = Logger.getLogger(Answer.class.getName());
	private String channelId;

	public Ring(CallController callController, String channelId) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.channelId = channelId;
	}

	/**
	 * The method ring to another channel
	 */
	@Override
	public CompletableFuture<Ring> run() {
		try {
			getAri().channels().ring(channelId);
		} catch (RestException e) {
			logger.severe("Unable to ring to channel with id: "+ channelId+" :"+e);
			return FutureHelper.completedExceptionally(new RingException(e));
		}
		return CompletableFuture.completedFuture(this);
	}
}

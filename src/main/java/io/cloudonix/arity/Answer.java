package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.AnswerCallException;
import io.cloudonix.future.helper.FutureHelper;

/**
 * The class represents the Answer operation (handle answering a call)
 * 
 * @author naamag
 *
 */
public class Answer extends Operation {

	private final static Logger logger = Logger.getLogger(Answer.class.getName());
	private String channelId = null;

	public Answer(CallController callController) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
	}

	/**
	 * The method answers a call that was received from an ARI channel
	 * 
	 * @return
	 * @throws AnswerCallException
	 */
	public CompletableFuture<Answer> run() {
		try {
			if(Objects.isNull(channelId))
				getAri().channels().answer(getChannelId());
			else
				getAri().channels().answer(channelId);
		} catch (RestException e) {
			logger.severe("Failed answering the call: " + e);
			return FutureHelper.completedExceptionally(new AnswerCallException(e));
		}
		logger.info("Call was answered");

		return CompletableFuture.completedFuture(this);
	}
}

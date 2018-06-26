package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.AnswerCallException;
import io.cloudonix.future.helper.FutureHelper;

/**
 * The class represents the Answer operation (handel answering a call) 
 * @author naamag
 *
 */
public class Answer extends Operation {
	
	private final static Logger logger = Logger.getLogger(Answer.class.getName());
	
	public Answer(CallController callController) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
	}
	
	/**
	 * The method answer a call that was received from an ARI channel
	 * 
	 * @param ari
	 * @param ss
	 * @return
	 * @throws AnswerCallException
	 */
	public CompletableFuture<Answer> run () {
		try {
			// answer the call
			getAri().channels().answer(getChannelId());
		} catch (RestException e) {
			logger.severe("Failed answering the call: " + e);
			return FutureHelper.completedExceptionally(new AnswerCallException(e));
		}
		logger.info("Call was answered");

		return CompletableFuture.completedFuture(this);
	}

}

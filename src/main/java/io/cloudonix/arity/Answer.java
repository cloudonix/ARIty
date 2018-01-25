package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.AnswerCallException;
import io.cloudonix.arity.errors.HangUpException;

public class Answer extends Operation {
	private final static Logger logger = Logger.getLogger(Answer.class.getName());
	
	public Answer(Call call) {
		super(call.getChannelID(), call.getARItyService(), call.getAri());
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
			getAri().channels().answer(getChanneLID());
		} catch (RestException e) {
			logger.severe("failed in answering the call: " + e);
			return completedExceptionally(new HangUpException(e));
		}
		logger.info("call answered");

		return CompletableFuture.completedFuture(this);
	}

}

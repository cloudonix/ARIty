package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.AnswerCallException;

public class Answer extends Verb {
	CompletableFuture<Void> compFuture;
	private final static Logger logger = Logger.getLogger(Answer.class.getName());
	
	public Answer(Call call) {
		super(call.getChannelID(), call.getService(), call.getAri());
		compFuture = new CompletableFuture<>();
	}
	
	/**
	 * The method answer a call that was received from an ARI channel
	 * 
	 * @param ari
	 * @param ss
	 * @return
	 * @throws AnswerCallException
	 */
	public CompletableFuture<Void> run () {
		try {
			// answer the call
			getAri().channels().answer(getChanneLID());
		} catch (RestException e) {
			logger.severe("failed in answering the call: " + e);
			compFuture.completeExceptionally(new AnswerCallException(e));
			return compFuture;
		}

		logger.info("call answered");

		return CompletableFuture.completedFuture(null);
	}

}

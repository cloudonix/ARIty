package io.cloudonix.service;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.service.errors.AnswerCallException;

public class Answer extends Verb {
	CompletableFuture<Void> cf;
	private final static Logger logger = Logger.getLogger(Answer.class.getName());
	
	public Answer(Call call) {
		super(call.getChannelID(), call.getService(), call.getAri());
		cf = new CompletableFuture<>();
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
			cf.completeExceptionally(new AnswerCallException(e));
			return cf;
		}

		logger.info("call answered");

		return CompletableFuture.completedFuture(null);
	}

}

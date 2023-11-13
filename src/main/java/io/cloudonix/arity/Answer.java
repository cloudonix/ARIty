package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudonix.arity.errors.dial.FailedToAnswerChannel;

/**
 * Answer the current call (send back SIP 200 Ok).
 * @author naamag
 * @author odeda
 */
public class Answer extends Operation {

	private final static Logger logger = LoggerFactory.getLogger(Answer.class);

	public Answer(CallController callController) {
		super(callController.getChannelId(), callController.getARIty());
	}

	/**
	 * The method answers a call that was received from an ARI channel
	 * @return a promise that will resolve when the answer operation has completed or reject with
	 * {@link FailedToAnswerChannel} if the channel wasn't found or Asterisk failed to answer the channel
	 */
	public CompletableFuture<Answer> run() {
		return this.<Void>retryOperation(cb -> channels().answer(getChannelId()).execute(cb)).thenApply(res -> {
			logger.info("Channel with id: {} was answered", getChannelId());
			return this;
		});
	}

	@Override
	protected Exception tryIdentifyError(Throwable ariError) {
		if (ariError.getMessage() == null)
			LoggerFactory.getLogger(getClass()).error("ARI error with no message???", ariError);
		else
			switch (ariError.getMessage()) {
			case "Channel not found": return new FailedToAnswerChannel(ariError);
			case "Failed to answer channel": return new FailedToAnswerChannel(ariError);
			}
		return null;
	}

}

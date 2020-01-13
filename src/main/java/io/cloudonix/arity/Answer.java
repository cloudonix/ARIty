package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import io.cloudonix.arity.errors.dial.FailedToAnswerChannel;

/**
 * The class represents the Answer operation (handle answering a call)
 *
 * @author naamag
 *
 */
public class Answer extends Operation {

	private final static Logger logger = Logger.getLogger(Answer.class.getName());

	public Answer(CallController callController) {
		super(callController.getChannelId(), callController.getARIty());
	}

	/**
	 * The method answers a call that was received from an ARI channel
	 *
	 * @return
	 */
	public CompletableFuture<Answer> run() {
		return this.<Void>retryOperation(cb -> channels().answer(getChannelId()).execute(cb)).thenApply(res -> {
			logger.info("Channel with id: " + getChannelId() + " was answered");
			return this;
		});
	}

	@Override
	protected Exception tryIdentifyError(Throwable ariError) {
		if (ariError.getMessage().contains("Failed to answer channel")) return new FailedToAnswerChannel(ariError);
		return super.tryIdentifyError(ariError);
	}

}

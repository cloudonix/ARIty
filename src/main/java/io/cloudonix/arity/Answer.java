package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.AnswerCallException;

/**
 * The class represents the Answer operation (handle answering a call)
 * 
 * @author naamag
 *
 */
public class Answer extends Operation {

	private final static Logger logger = Logger.getLogger(Answer.class.getName());

	public Answer(CallController callController) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
	}

	/**
	 * The method answers a call that was received from an ARI channel
	 * 
	 * @return
	 */
	public CompletableFuture<Answer> run() {
		CompletableFuture<Answer> future = new CompletableFuture<Answer>();
		getAri().channels().answer(getChannelId(), new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.info("Call with channel: "+getChannelId()+" was answered");
				future.complete(null);
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("Failed answer the call: " + e);
				future.completeExceptionally(new AnswerCallException(e));
			}
		});
		return future;
	}
}

package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;

public class ReceivedDTMF extends Operation {

	private CompletableFuture<ReceivedDTMF> compFuture;
	private String userInput;
	private final static Logger logger = Logger.getLogger(ReceivedDTMF.class.getName());
	private List<CancelableOperations> nestedOperations;
	private String terminatingKey;
	private CancelableOperations currOpertation = null;

	/**
	 * Constructor
	 * 
	 * @param callController
	 * @param termKey
	 *            - define terminating key (otherwise '#' is the default)
	 */
	public ReceivedDTMF(CallController callController, String termKey) {

		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		compFuture = new CompletableFuture<>();
		userInput = "";
		terminatingKey = termKey;
		nestedOperations = new ArrayList<>();
	}

	/**
	 * Constructor
	 * 
	 * @param callController
	 */
	public ReceivedDTMF(CallController callController) {

		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		compFuture = new CompletableFuture<>();
		userInput = "";
		terminatingKey = "#";
		nestedOperations = new ArrayList<>();
	}

	/**
	 * The method gathers input from the user
	 * 
	 * @param terminatingKey
	 *            - the digit that stops the gathering
	 * @return
	 */
	public CompletableFuture<? extends Operation> run() {

		// if the list of verbs is not empty, execute them one by one
		if (!nestedOperations.isEmpty()) {
			logger.info("there are verbs in the nested verb list");

			currOpertation = nestedOperations.get(0);
			CompletableFuture<? extends Operation> future = nestedOperations.get(0).run();

			if (nestedOperations.size() > 1 && Objects.nonNull(future)) {
				for (int i = 1; i < nestedOperations.size() && Objects.nonNull(future); i++) {
					currOpertation = nestedOperations.get(i);
					future = loopOperations(future, nestedOperations.get(i));
				}
			}

		}

		getArity().addFutureEvent(ChannelDtmfReceived.class, dtmf -> {
			if (!(dtmf.getChannel().getId().equals(getChanneLID()))) {
				return false;
			}

			if (dtmf.getDigit().equals(terminatingKey)) {
				logger.info("the terminating key is: " + terminatingKey);
				logger.info("all input: " + userInput);
				if (Objects.nonNull(currOpertation))
					// cancel the current operation because the gather is finished
					currOpertation.cancel();

				compFuture.complete(this);
				return true;

			}
			userInput = userInput + dtmf.getDigit();
			return false;

		});

		return compFuture;
	}

	/**
	 * compose the previous future (of the previous verb) to the result of the new
	 * future
	 * 
	 * @param future
	 * @param operation
	 * @return
	 */
	private CompletableFuture<? extends Operation> loopOperations(CompletableFuture<? extends Operation> future,
			Operation operation) {
		logger.info("The current nested operation is: " + currOpertation.toString());
		return future.thenCompose(pb -> {
			if (Objects.nonNull(pb))
				// if the previous future (previous playback) is not null, run it
				return operation.run();
			return CompletableFuture.completedFuture(null);
		});
	}

	/**
	 * add new operation to list of nested operation that run method will execute
	 * one by one
	 * 
	 * @param operation
	 * @return
	 */

	public ReceivedDTMF and(CancelableOperations operation) {
		nestedOperations.add(operation);
		return this;
	}

	/**
	 * set the terminating key
	 * 
	 * @param termKey
	 * @return
	 */
	public ReceivedDTMF termKey(String termKey) {
		terminatingKey = termKey;
		return this;
	}

	/**
	 * return the entire input that was gathered
	 * 
	 * @return
	 */
	public String getInput() {
		return userInput;
	}

}

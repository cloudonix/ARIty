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
	 * @param call
	 * @param termKey - define terminating key (otherwise '#' is the default)
	 */
	public ReceivedDTMF(Call call, String termKey) {

		super(call.getChannelID(), call.getService(), call.getAri());
		compFuture = new CompletableFuture<>();
		userInput = "";
		terminatingKey = termKey;
		nestedOperations = new ArrayList<>();
	}

	/**
	 * Constructor
	 * 
	 * @param call
	 */
	public ReceivedDTMF(Call call) {

		super(call.getChannelID(), call.getService(), call.getAri());
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
		
		// if the list of verbs is not empty, execute them one by one- wait until the
		// current verb is finished before continuing to the next on (the completable
		// future is completed )
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

		getService().addFutureEvent(ChannelDtmfReceived.class, dtmf -> {
			if (!(dtmf.getChannel().getId().equals(getChanneLID()))) {
				return false;
			}

			// if the input is the terminating key "#" then stop
			if (dtmf.getDigit().equals(terminatingKey)) {
				logger.info("all input: " + userInput);
				
				// cancel the current operation because the gather is finished
				currOpertation.cancel();
				compFuture.complete(this);
				return true;

			}
			// the input is 0-9 or A-E or * - save it with the previous digits
			userInput = userInput + dtmf.getDigit();
			return false;

		});

		return compFuture;
	}


	/**
	 * compose the previous future (of the previous verb) to the result of the new future
	 * @param future
	 * @param operation
	 * @return
	 */
	private CompletableFuture<? extends Operation> loopOperations(CompletableFuture<? extends Operation> future, Operation operation) {
		logger.info("The current nested operation is: " + currOpertation.toString());
		return future.thenCompose(pb -> {
			if (Objects.nonNull(pb))
				// if the previous future (previous playback) is not null, run it
				return operation.run();
			return CompletableFuture.completedFuture(null);
		});
	}

	/**
	 * add new verb to list of nested verbs that run method will execute one by one
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

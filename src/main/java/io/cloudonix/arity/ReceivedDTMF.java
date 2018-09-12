package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;

/**
 * The class represents the Received DTMF operation
 * 
 * @author naamag
 *
 */
public class ReceivedDTMF extends Operation {

	private CompletableFuture<ReceivedDTMF> compFuture = new CompletableFuture<>();
	private String userInput = "";
	private final static Logger logger = Logger.getLogger(ReceivedDTMF.class.getName());
	private List<CancelableOperations> nestedOperations = new ArrayList<>();;
	private String terminatingKey;
	private CancelableOperations currOpertation = null;
	private int inputLength;
	private int currOpIndext;
	private boolean isCanceled;
	private boolean termKeyWasPressed = false;
	private transient String firsFinishedInput = "";
	private Record recordOperation;
	private boolean doneAllOps = false;

	/**
	 * Constructor
	 * 
	 * @param callController
	 *            call instance
	 * @param termKey
	 *            define terminating key (otherwise '#' is the default)
	 * @param length
	 *            length of the input we are expecting to get from the caller. for
	 *            no limitation -1
	 */
	public ReceivedDTMF(CallController callController, String termKey, int length) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.terminatingKey = termKey;
		this.inputLength = length;
	}

	/**
	 * Constructor with default values
	 * 
	 * @param callController
	 */
	public ReceivedDTMF(CallController callController) {
		this(callController, "#", -1);
	}

	/**
	 * The method gathers input from the user
	 * 
	 * @return
	 */
	public CompletableFuture<ReceivedDTMF> run() {
		getArity().addFutureEvent(ChannelDtmfReceived.class, getChannelId(), dtmf -> {
			if (dtmf.getDigit().equals(terminatingKey) || Objects.equals(inputLength, userInput.length())) {
				logger.info("Done receiving DTMF. all input: " + userInput);
				if (dtmf.getDigit().equals(terminatingKey)) {
					termKeyWasPressed = true;
					cancelAll().thenApply(v -> {
						compFuture.complete(this);
						return true;
					});
					if (Objects.equals(firsFinishedInput, "")) {
						firsFinishedInput = "dtmf";
						logger.info("first finished input was received via dtmf");
					}
				}
			}
			
			if (!termKeyWasPressed)
				userInput = userInput + dtmf.getDigit();
			return false;
		}, false);

		if (!nestedOperations.isEmpty()) {
			logger.fine("there are verbs in the nested operation list");
			currOpertation = nestedOperations.get(0);
			currOpIndext = 0;
			CompletableFuture<? extends Operation> future = CompletableFuture.completedFuture(null);
			for (int i = currOpIndext; i < nestedOperations.size(); i++) {
				future = future.thenCompose(res -> {
					if (!isCanceled) {
						currOpertation = nestedOperations.get(currOpIndext);
						currOpIndext++;
						return currOpertation.run();
					}
					return CompletableFuture.completedFuture(null);
				});
			}
			if (currOpIndext == nestedOperations.size() && !isCanceled)
				doneAllOps = true;
		}
		return compFuture;
	}

	/**
	 * When stopped receiving DTMF cancel all operations
	 */
	private CompletableFuture<Void> cancelAll() {
		if (!isCanceled) {
			isCanceled = true;
			for (int i = currOpIndext - 1; i < nestedOperations.size(); i++) {
				nestedOperations.get(i).cancel();
			}
		}
		return CompletableFuture.completedFuture(null);
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
	public ReceivedDTMF setTerminatingKey(String termKey) {
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


	public boolean isTermKeyWasPressed() {
		return termKeyWasPressed;
	}

	public void setTermKeyWasPressed(boolean termKeyWasPressed) {
		this.termKeyWasPressed = termKeyWasPressed;
	}

	public String getFirsFinishedInput() {
		return firsFinishedInput;
	}

	public void setFirsFinishedInput(String firsFinishedInputVia) {
		this.firsFinishedInput = firsFinishedInputVia;
	}

	public Record getRecordOperation() {
		return recordOperation;
	}

	public void setRecordOperation(Record recordOperation) {
		this.recordOperation = recordOperation;
	}

	public boolean isDoneAllOps() {
		return doneAllOps;
	}

	public void setDoneAllOps(boolean doneAllOps) {
		this.doneAllOps = doneAllOps;
	}
}

package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;
import io.cloudonix.future.helper.FutureHelper;

/**
 * The class represents the Received DTMF operation
 * 
 * @author naamag
 *
 */
public class ReceivedDTMF extends CancelableOperations {
	private String userInput = "";
	private final static Logger logger = Logger.getLogger(ReceivedDTMF.class.getName());
	private List<CancelableOperations> nestedOperations = new ArrayList<>();;
	private String terminatingKey;
	private CancelableOperations currOpertation = null;
	private int inputLength;
	private int currOpIndext;
	private boolean isCanceled = false;
	private boolean termKeyWasPressed = false;
	private boolean doneAllOps = false;
	private CompletableFuture<ReceivedDTMF> compFuture = new CompletableFuture<>();

	/**
	 * Constructor
	 * 
	 * @param callController call instance
	 * @param termKey        define terminating key (otherwise '#' is the default)
	 * @param length         length of the input we are expecting to get from the
	 *                       caller. for no limitation -1
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
		getArity().addFutureEvent(ChannelDtmfReceived.class, getChannelId(), this::handleDTMF);

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
	 * handle DTMF events
	 * 
	 * @param dtmf dtmf event
	 * @param se the saved event handler for dtmf
	 */
	public void handleDTMF(ChannelDtmfReceived dtmf, SavedEvent<ChannelDtmfReceived>se) {
		if (dtmf.getDigit().equals(terminatingKey)) {
			logger.info("Done receiving DTMF. all input: " + userInput);
			termKeyWasPressed = true;
			cancelAll().thenAccept(v -> compFuture.complete(this));
			se.unregister();
			return;
		}
		userInput = userInput + dtmf.getDigit();
		if (Objects.equals(inputLength, userInput.length())) {
			cancelAll().thenAccept(v -> compFuture.complete(this));
			se.unregister();
			return;
		}
	}

	/**
	 * When stopped receiving DTMF, cancel all operations
	 */
	private CompletableFuture<Void> cancelAll() {
		if (!isCanceled) {
			isCanceled = true;
			for (int i = (currOpIndext == 0) ? currOpIndext : currOpIndext - 1; i < nestedOperations.size(); i++) {
				nestedOperations.get(i).cancel();
			}
		}
		return FutureHelper.completedFuture();
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

	public boolean isDoneAllOps() {
		return doneAllOps;
	}

	public void setDoneAllOps(boolean doneAllOps) {
		this.doneAllOps = doneAllOps;
	}

	@Override
	public CompletableFuture<Void> cancel() {
		logger.info("Cancelling receiving DTMF");
		isCanceled = true;
		return cancelAll().thenAccept(v -> {
			compFuture.complete(this);
		});
	}

	public boolean isCanceled() {
		return isCanceled;
	}

	public void setCanceled(boolean isCanceled) {
		this.isCanceled = isCanceled;
	}
}

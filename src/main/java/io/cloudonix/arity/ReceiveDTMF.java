package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.models.ChannelDtmfReceived;
import io.cloudonix.arity.CallState.States;

/**
 * Register for receiving DTMF sequences
 * @author naamag
 * @author odeda
 */
public class ReceiveDTMF extends CancelableOperations {
	private String userInput = "";
	private final static Logger logger = LoggerFactory.getLogger(ReceiveDTMF.class);
	private String terminatingKey = "";
	private int inputLength = -1;
	private boolean termKeyWasPressed = false;
	private CompletableFuture<ReceiveDTMF> compFuture = new CompletableFuture<>();
	private EventHandler<ChannelDtmfReceived> handler;
	private Consumer<String> applicationDTMFHandler = v -> {};

	/**
	 * Create a new DTMF receiver with both a terminating key list and a maximum input length
	 * @param callController call instance
	 * @param termKeys DTMF signals that will terminate the DTMF receiver (cause the {@link #run()} completion to resolve).
	 * 	Specify the empty string for no automatic termination.
	 * @param length the maximum number of DTMF signals that can be received, after which the DTMF receiver will terminate.
	 * 	Specify -1 for no maximum.
	 */
	public ReceiveDTMF(CallController callController, String termKeys, int length) {
		this(callController);
		this.terminatingKey = Objects.requireNonNull(termKeys);
		this.inputLength = length;
	}
	
	/**
	 * Create a new DTMF receiver with just a terminating key list and no maximum length
	 * @param callController call instance
	 * @param termKeys DTMF signals that will terminate the DTMF receiver (cause the {@link #run()} completion to resolve).
	 * 	Specify the empty string for no automatic termination.
	 */
	public ReceiveDTMF(CallController callController, String termKeys) {
		this(callController);
		this.terminatingKey = termKeys;
	}
	
	/**
	 * Create a new DTMF receiver with just a maximum input length and no terminating key list
	 * @param callController call instance
	 * @param length the maximum number of DTMF signals that can be received, after which the DTMF receiver will terminate.
	 * 	Specify -1 for no maximum.
	 */
	public ReceiveDTMF(CallController callController, int length) {
		this(callController);
		this.inputLength = length;
	}
	
	/**
	 * Create a new DTMF receiver with no stop conditions (you must call {@link #cancel()} manually to stop receiving DTMF
	 * @param callController call instance
	 */
	public ReceiveDTMF(CallController callController) {
		super(callController.getChannelId(), callController.getARIty());
		callController.getCallState().registerStateHandler(States.Hangup, this::cancel);
	}

	/**
	 * Start gathering DTMF input
	 * @return a promise that will complete when stop conditions (terminating key or max length) have been reached, or the operation was cancelled
	 */
	public CompletableFuture<ReceiveDTMF> run() {
		this.handler = getArity().addEventHandler(ChannelDtmfReceived.class, getChannelId(), this::handleDTMF);
		return compFuture;
	}

	/**
	 * handle DTMF events
	 *
	 * @param dtmf dtmf event
	 * @param se the saved event handler for dtmf
	 */
	public void handleDTMF(ChannelDtmfReceived dtmf, EventHandler<ChannelDtmfReceived>se) {
		applicationDTMFHandler.accept(dtmf.getDigit());
		if (dtmf.getDigit().equals(terminatingKey)) {
			logger.info("Done receiving DTMF. all input: " + userInput);
			termKeyWasPressed = true;
			cancel();
			return;
		}
		userInput = userInput + dtmf.getDigit();
		if (Objects.equals(inputLength, userInput.length())) {
			cancel();
			return;
		}
	}

	/**
	 * set the terminating key
	 *
	 * @param termKey
	 * @return
	 */
	public ReceiveDTMF setTerminatingKey(String termKey) {
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

	/**
	 * unregister from listening to DTMF events
	 */
	@Override
	public CompletableFuture<Void> cancel() {
		unregister(this.handler);
		return CompletableFuture.completedFuture(null);
	}

	/**
	 * unregister from listening to DTMF events
	 * @param se saved event we want to unregister from it
	 */
	public void unregister(EventHandler<ChannelDtmfReceived>se) {
		se.unregister();
		compFuture.complete(this);
	}

	/**
	 * Register a callback to receive DTMF events
	 * @param handler callback to handle DTMF events
	 * @return itself for call chaining
	 */
	public ReceiveDTMF registerHandler(Consumer<String> handler) {
		this.applicationDTMFHandler = Objects.requireNonNull(handler);
		return this;
	}

}

package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.models.ChannelDtmfReceived;
import io.cloudonix.lib.Timers;

/**
 * Register for receiving DTMF sequences
 * @author naamag
 * @author odeda
 */
public class ReceiveDTMF extends CancelableOperations {
	private String userInput = "";
	private final static Logger logger = LoggerFactory.getLogger(ReceiveDTMF.class);
	private String terminatingKey = "#";
	private int inputLength = -1;
	private boolean termKeyWasPressed = false;
	private CompletableFuture<ReceiveDTMF> compFuture = new CompletableFuture<>();
	private EventHandler<ChannelDtmfReceived> handler;
	private Consumer<String> applicationDTMFHandler = v -> {};

	/**
	 * Create a new DTMF receiver
	 * @param callController call instance
	 * @param termKey        define terminating key (otherwise '#' is the default)
	 * @param length         length of the input we are expecting to get from the
	 *                       caller. for no limitation -1
	 */
	public ReceiveDTMF(CallController callController, String termKey, int length) {
		this(callController);
		this.terminatingKey = termKey;
		this.inputLength = length;
	}
	
	/**
	 * Create a new DTMF receiver with just a terminating key stop condition
	 * @param callController call instance
	 * @param termKey        define terminating key (otherwise '#' is the default)
	 */
	public ReceiveDTMF(CallController callController, String termKey) {
		this(callController);
		this.terminatingKey = termKey;
	}
	
	/**
	 * Create a new DTMF receiver with just a length stop condition
	 * @param callController call instance
	 * @param length         length of the input we are expecting to get from the
	 *                       caller. for no limitation -1
	 */
	public ReceiveDTMF(CallController callController, int length) {
		this(callController);
		this.inputLength = length;
		this.terminatingKey = null;
	}
	
	/**
	 * Create a new DTMF receiver with a default terminating key stop condition
	 * @param callController call instance
	 */
	public ReceiveDTMF(CallController callController) {
		super(callController.getChannelId(), callController.getARIty());
		Timers.schedule(this::cancel, TimeUnit.HOURS.toMillis(2));
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

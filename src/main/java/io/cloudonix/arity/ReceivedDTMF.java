package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;

/**
 * The class represents the Received DTMF events
 * 
 * @author naamag
 *
 */
public class ReceivedDTMF {
	private String userInput = "";
	private final static Logger logger = Logger.getLogger(ReceivedDTMF.class.getName());
	private String terminatingKey;
	private int inputLength;
	private boolean termKeyWasPressed = false;
	private CompletableFuture<ReceivedDTMF> compFuture = new CompletableFuture<>();
	private ARIty arity;
	private String channelId;
	private BiConsumer<ChannelDtmfReceived, EventHandler<ChannelDtmfReceived>> runDtmfHandler = null;

	/**
	 * Constructor
	 * 
	 * @param callController call instance
	 * @param termKey        define terminating key (otherwise '#' is the default)
	 * @param length         length of the input we are expecting to get from the
	 *                       caller. for no limitation -1
	 */
	public ReceivedDTMF(CallController callController, String termKey, int length) {
		this.terminatingKey = termKey;
		this.inputLength = length;
		this.arity = callController.getARIty();
		this.channelId = callController.getChannelId();
		
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
		arity.addEventHandler(ChannelDtmfReceived.class, channelId, this::handleDTMF);
		return compFuture;
	}

	/**
	 * handle DTMF events
	 * 
	 * @param dtmf dtmf event
	 * @param se the saved event handler for dtmf
	 */
	public void handleDTMF(ChannelDtmfReceived dtmf, EventHandler<ChannelDtmfReceived>se) {
		if(Objects.nonNull(runDtmfHandler)) {
			runDtmfHandler.accept(dtmf,se); // execute function from an app when receiving DTMF, need to unregister also when done
			return;
		}
		if (dtmf.getDigit().equals(terminatingKey)) {
			logger.info("Done receiving DTMF. all input: " + userInput);
			termKeyWasPressed = true;
			unregister(se);
			return;
		}
		userInput = userInput + dtmf.getDigit();
		if (Objects.equals(inputLength, userInput.length())) {
			unregister(se);
			return;
		}
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
	
	/**
	 * unregister from listening to DTMF events
	 * 
	 * @param se saved event we want to unregister from it
	 */
	public void unregister(EventHandler<ChannelDtmfReceived>se) {
		se.unregister();
		compFuture.complete(this);
	}
	
	/**
	 * register an handler that will run when DTMF events arrives
	 * 
	 * @param handler
	 */
	public void registerHandler(BiConsumer<ChannelDtmfReceived, EventHandler<ChannelDtmfReceived>> handler) {
		this.runDtmfHandler = handler;
	}
}

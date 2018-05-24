package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;
import ch.loway.oss.ari4java.generated.ChannelTalkingFinished;

/**
 * The class represents the RecivedDTMF operation (collects/gather the input from the user)
 * @author naamag
 *
 */
public class ReceivedDTMF extends Operation {
	
	private CompletableFuture<ReceivedDTMF> compFuture = new CompletableFuture<>();
	private String userInput = "#";
	private final static Logger logger = Logger.getLogger(ReceivedDTMF.class.getName());
	private List<CancelableOperations> nestedOperations = new ArrayList<>();;
	private String terminatingKey;
	private CancelableOperations currOpertation = null;
	private int inputLenght = -1;
	private boolean termKeyWasPressed = false;
	// duration of talking to the channel in milliseconds
	private int channelTalkDuration = 0;

	/**
	 * Constructor
	 * 
	 * @param callController
	 * @param termKey
	 *            define terminating key (otherwise '#' is the default)
	 * @param lenght lenght of the input we are expecting to get from the caller. for no limitation -1
	 */
	public ReceivedDTMF(CallController callController, String termKey, int lenght) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		terminatingKey = termKey;
		inputLenght = lenght;
	}

	/**
	 * Constructor
	 * 
	 * @param callController
	 */
	public ReceivedDTMF(CallController callController) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
	}

	/**
	 * The method gathers input from the user
	 * 
	 * @param terminatingKey
	 *            - the digit that stops the gathering
	 * @return
	 */
	public CompletableFuture<ReceivedDTMF> run() {
		if (!nestedOperations.isEmpty()) {
			logger.info("there are verbs in the nested verb list");

			currOpertation = nestedOperations.get(0);
			CompletableFuture<? extends Operation> future = nestedOperations.get(0).run();

			if (nestedOperations.size() > 1 && Objects.nonNull(future)) {
				for (int i = 1; i < nestedOperations.size() && Objects.nonNull(future); i++) {
					currOpertation = nestedOperations.get(i);
					future = loopOperations(future);
				}
			}

		}
		// stop receiving DTMF when terminating key was pressed or when talking in the channel has finished
		getArity().addFutureEvent(ChannelDtmfReceived.class, dtmf -> {
			if (!(dtmf.getChannel().getId().equals(getChannelId())))
				return false;

			if (dtmf.getDigit().equals(terminatingKey) || Objects.equals(inputLenght, userInput.length())) {
				logger.info("done gathering. all input: " + userInput);
				if (Objects.nonNull(currOpertation))
					currOpertation.cancel();

				compFuture.complete(this);
				return true;

			}
			userInput = userInput + dtmf.getDigit();
			return false;
		});
		
		getArity().addFutureEvent(ChannelTalkingFinished.class, speech->{
			if(Objects.equals(speech.getChannel().getId(), getChannelId())) {
				logger.info("talking to the channel is finished, stop gathering");
				channelTalkDuration = speech.getDuration();
				if (Objects.nonNull(currOpertation))
					currOpertation.cancel();

				compFuture.complete(this);
				return true;
			}
			return false;
		});

		return compFuture;
	}

	/**
	 * compose the previous future (of the previous verb) to the result of the new
	 * future
	 * 
	 * @param future future of the previous operation
	 * @return
	 */
	private CompletableFuture<? extends Operation> loopOperations(CompletableFuture<? extends Operation> future) {
		logger.info("The current nested operation is: " + currOpertation.toString());
		return future.thenCompose(op -> {
			if (Objects.nonNull(op))
				return currOpertation.run();
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
	/**
	 * check if the terminating key was pressed- true if yes, false otherwise
	 * @return
	 */
	public boolean isTermKeyWasPressed() {
		return termKeyWasPressed;
	}
	/**
	 * get time of talking in the channel in milliseconds
	 * @return
	 */
	public int getChannelTalkDuration() {
		return channelTalkDuration;
	}

}

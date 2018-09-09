package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;
import ch.loway.oss.ari4java.generated.LiveRecording;

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
	private boolean isDTMF;
	private boolean isSpeech;
	private CallController callController;
	private String recordName = "";
	private int speechMaxSilence;
	private LiveRecording recording;
	private int speechMaxDuration;
	private int currOpIndext;
	private boolean isCanceled;
	private boolean termKeyWasPressed = false;;

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
	 * @param isDTMF
	 *            true if the input should be received via DTMF, false if the input
	 *            should be received via speech (talking detection in the channel).
	 * @param isSpeech
	 *            true if the input should be received via speech (talking detection
	 *            in the channel), false if the input should be received via DTMF
	 * @param speechMaxDuration
	 *            maximum duration for recording the speech
	 * @param speechMaxSilence
	 *            maximum duration of silence allowed before stop recording speech
	 *            (same as maxSilenceSeconds in channel record)
	 */
	public ReceivedDTMF(CallController callController, String termKey, int length, boolean isDTMF, boolean isSpeech,
			int speechMaxDuration, int speechMaxSilence) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.callController = callController;
		this.terminatingKey = termKey;
		this.inputLength = length;
		this.isDTMF = isDTMF;
		this.isSpeech = isSpeech;
		this.speechMaxDuration = speechMaxDuration;
		this.speechMaxSilence = speechMaxSilence;
		// default of receiving DTMF is via DTMF if non was given
		if (!isDTMF && !isSpeech)
			this.isDTMF = true;
	}

	/**
	 * Constructor with default values
	 * 
	 * @param callController
	 */
	public ReceivedDTMF(CallController callController) {
		this(callController, "#", -1, true, false, (int) TimeUnit.HOURS.toSeconds(1), 0);
	}

	/**
	 * The method gathers input from the user
	 * 
	 * @param terminatingKey
	 *            - the digit that stops the gathering
	 * @return
	 */
	public CompletableFuture<ReceivedDTMF> run() {
		if (isDTMF) {
			getArity().addFutureEvent(ChannelDtmfReceived.class, getChannelId(), dtmf -> {
				if (dtmf.getDigit().equals(terminatingKey) || Objects.equals(inputLength, userInput.length())) {
					logger.info("Done receiving DTMF. all input: " + userInput);
					if (dtmf.getDigit().equals(terminatingKey)) {
						termKeyWasPressed  = true;
						cancelAll().thenApply(v -> {
							compFuture.complete(this);
							return true;
						});
					}
				}
				if(!termKeyWasPressed)
					userInput = userInput + dtmf.getDigit();
				return false;
			}, false);
		}
		if (isSpeech) {
			recordName = UUID.randomUUID().toString();
			callController.record(recordName, ".wav", speechMaxDuration, speechMaxSilence, false, terminatingKey).run()
					.thenCompose(recordRes -> {
						return cancelAll().thenAccept(v -> {
							recording = recordRes.getRecording();
							compFuture.complete(this);
						});
					});
		}
		
		if (!nestedOperations.isEmpty()) {
			logger.fine("there are verbs in the nested operation list");
			currOpertation = nestedOperations.get(0);
			currOpIndext = 0;
			CompletableFuture<? extends Operation> future = CompletableFuture.completedFuture(null);
				for(int i = currOpIndext; i< nestedOperations.size(); i++) {
						future = future.thenCompose(res ->{
							if(!isCanceled) {
								currOpertation = nestedOperations.get(currOpIndext);
								currOpIndext++;
								return currOpertation.run();
							}
							return CompletableFuture.completedFuture(null);
						});
				}
		}
		return compFuture;
	}

	/**
	 * When stopped receiving DTMF cancel all operations
	 */
	private CompletableFuture<Void> cancelAll() {
		isCanceled = true;
		for (int i = currOpIndext-1; i < nestedOperations.size(); i++) {
			nestedOperations.get(i).cancel();
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

	public LiveRecording getRecording() {
		return recording;
	}

	public void setRecording(LiveRecording recording) {
		this.recording = recording;
	}
}

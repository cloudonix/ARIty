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
		if (!nestedOperations.isEmpty()) {
			logger.fine("there are verbs in the nested operation list");
			currOpertation = nestedOperations.get(0);
			CompletableFuture<? extends Operation> future = currOpertation.run();
			if (nestedOperations.size() > 1 && Objects.nonNull(future)) {
				for (int i = 1; i < nestedOperations.size() && Objects.nonNull(future); i++) {
					currOpertation = nestedOperations.get(i);
					future.thenCompose(res -> loopOperations(future));
				}
			}
		}

		if (isDTMF) {
			getArity().addFutureEvent(ChannelDtmfReceived.class, getChannelId(), dtmf -> {
				if (dtmf.getDigit().equals(terminatingKey) || Objects.equals(inputLength, userInput.length())) {
					logger.info("Done receiving DTMF. all input: " + userInput);
					if (dtmf.getDigit().equals(terminatingKey)) {
						cancelAll();
						compFuture.complete(this);
						return true;
					}
				}
				userInput = userInput + dtmf.getDigit();
				return false;
			}, false);
		}

		if (isSpeech) {
			recordName = UUID.randomUUID().toString();
			callController.record(recordName, ".wav", speechMaxDuration, speechMaxSilence, false, terminatingKey).run()
					.thenAccept(recordRes -> {
						cancelAll();
						recording = recordRes.getRecording();
						compFuture.complete(this);
					});
		}
		return compFuture;
	}

	/**
	 * When stopped receiving DTMF cancel all operations
	 */
	private void cancelAll() {
		if (Objects.nonNull(currOpertation))
			currOpertation.cancel();
		nestedOperations.clear();
	}

	/**
	 * compose the previous future (of the previous operation) to the result of the
	 * new future
	 * 
	 * @param future
	 *            future of the previous operation
	 * @return
	 */
	private CompletableFuture<? extends Operation> loopOperations(CompletableFuture<? extends Operation> future) {
		logger.info("The current operation is: " + currOpertation.toString());
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

	public LiveRecording getRecording() {
		return recording;
	}

	public void setRecording(LiveRecording recording) {
		this.recording = recording;
	}
}

package io.cloudonix.arity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.models.ChannelDtmfReceived;
import ch.loway.oss.ari4java.generated.models.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.models.ChannelTalkingStarted;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import ch.loway.oss.ari4java.generated.models.RecordingFinished;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.RecordingException;
import io.cloudonix.arity.errors.RecordingNotFoundException;
import io.cloudonix.lib.Futures;
import io.cloudonix.lib.Timers;

/**
 * Class for recording a channel operation
 *
 * @author naamag
 * @author odeda
 */
public class Record extends CancelableOperations {
	private String name;
	private String fileFormat = "ulaw";
	private int maxDuration; // maximum duration of the recording
	private int maxSilenceSeconds;
	private boolean beep;
	private String terminateOnKey;
	volatile private RecordingData recording;
	private CallController callController;
	private boolean isTermKeyWasPressed = false;
	private final static Logger logger = Logger.getLogger(Record.class.getName());
	volatile private boolean wasTalkingDetected = false;
	private int talkingDuration = 0;
	private String ifExists = "overwrite"; // can be set to the following values: fail, overwrite, append
	private Instant recordingStartTime;
	private CompletableFuture<Void> waitUntilDone = new CompletableFuture<>();
	private LinkedList<EventHandler<?>> eventHandlers = new LinkedList<>();
	private AtomicBoolean wasCancelled = new AtomicBoolean(false);

	/**
	 * Constructor
	 *
	 * @param callController    instant representing the call
	 * @param name              Recording's filename
	 * @param fileFormat        Format to encode audio in (wav, gsm..)
	 * @param maxDuration       Maximum duration of the recording, in seconds. 0 for
	 *                          no limit
	 * @param maxSilenceSeconds Maximum duration of silence before ending the
	 *                          recording, in seconds. 0 for no limit
	 * @param beep              true if we want to play beep when recording begins,
	 *                          false otherwise
	 * @param terminateOnKey    DTMF input to terminate recording (allowed values:
	 *                          none, any, *, #)
	 */

	public Record(CallController callController, String name, String fileFormat, int maxDuration, int maxSilenceSeconds,
			boolean beep, String terminateOnKey) {
		super(callController.getChannelId(), callController.getARIty());
		this.name = name;
		this.fileFormat = (Objects.nonNull(fileFormat) && !Objects.equals(fileFormat, "")) ? fileFormat
				: this.fileFormat;
		this.maxDuration = maxDuration;
		this.maxSilenceSeconds = maxSilenceSeconds;
		this.beep = beep;
		this.terminateOnKey = Objects.requireNonNullElse(terminateOnKey, "");
		callController.setTalkingInChannel("set", ""); // Enable talking detection in the channel
		if (beep)
			this.callController = callController;
	}

	@Override
	public CompletableFuture<Record> run() {
		return playBeep().thenCompose(res -> startRecording());
	}
	
	private CompletableFuture<Void> playBeep() {
		if (!beep)
			return CompletableFuture.completedFuture(null);
		logger.fine("Play beep before recording");
		return callController.play("beep").run().thenAccept(v -> {});
	}

	/**
	 * start recording
	 *
	 */
	private CompletableFuture<Record> startRecording() {
		setupHandlers();
		recording = new RecordingData(getArity(), name);
		return this.<LiveRecording>retryOperation(cb -> channels().record(getChannelId(), name, fileFormat)
				.setMaxDurationSeconds(maxDuration).setMaxSilenceSeconds(maxSilenceSeconds)
				.setIfExists(ifExists).setBeep(beep).execute(cb))
				.thenAccept(recording::setLiveRecording)
				.thenCompose(v -> {
					logger.info("Recording started! recording name is: " + name);
					Timers.schedule(this::stopRecording, TimeUnit.SECONDS.toMillis(maxDuration));
					return Objects.requireNonNull(waitUntilDone, "Error setting up recording");
				})
				.whenComplete((v,t) -> cleanupHandlers())
				.thenApply(v -> this)
				.exceptionally(Futures.on(RestException.class, e -> {
					throw new RecordingException(name, e);
				}));
	}

	private void setupHandlers() {
		recordingStartTime = Instant.now();
		eventHandlers.addAll(Arrays.asList(
				// wait until Asterisk says we're done
				getArity().addEventHandler(RecordingFinished.class, getChannelId(), (record, se) -> {
					String evName = record.getRecording().getName();
					if (!name.equals(evName))
						return;
					long duration = Math.abs(Instant.now().toEpochMilli() - recordingStartTime.toEpochMilli());
					logger.fine("Finished recording! recording duration is: " + duration + "ms, reported " + record.getRecording().getDuration() + "s");
					recording.setLiveRecording(record.getRecording());
					waitUntilDone.complete(null);
				}),
				
				// Recognize if Talking was detected during the recording
				getArity().listenForOneTimeEvent(ChannelTalkingStarted.class, getChannelId(), (talkStarted) -> {
					logger.info("Recognised tallking in the channel");
					wasTalkingDetected = true;
				}),
				
				// stop the recording by pressing the terminating key
				getArity().addEventHandler(ChannelDtmfReceived.class, getChannelId(), (dtmf, se) -> {
					if (!terminateOnKey.contains(dtmf.getDigit()))
						return;
					logger.info("Terminating key '"+dtmf.getDigit()+"' was pressed, stop recording");
					isTermKeyWasPressed = true;
					cancel();
				}),
				
				getArity().addEventHandler(ChannelHangupRequest.class, getChannelId(), (hangup, se) -> {
					cancel();
				})
				));
	}
	
	private void cleanupHandlers() {
		eventHandlers.forEach(EventHandler::unregister);
		eventHandlers.clear();
	}

	/**
	 * get the recording
	 *
	 * @return
	 */
	public RecordingData getRecording() {
		return recording;
	}

	/**
	 * get the name of the recording
	 *
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * return true if terminating key was pressed to stop the recording, false
	 * otherwise
	 *
	 * @return
	 */
	public boolean isTermKeyWasPressed() {
		return isTermKeyWasPressed;
	}

	/**
	 * return terminating key
	 *
	 * @return
	 */
	public String getTerminatingKey() {
		return terminateOnKey;
	}

	/**
	 * set the name of the recording
	 *
	 * @param name name of the recording
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * get the state of the recording
	 *
	 * @return
	 */
	public String getRecordingState() {
		return recording.hasRecording() ? recording.getRecording().getState() : "in-progress";
	}

	/**
	 * stop recording
	 *
	 * @return
	 */
	public CompletableFuture<Void> stopRecording() {
		return cancel();
	}

	@Override
	public CompletableFuture<Void> cancel() {
		if (wasCancelled.getAcquire())
			return Futures.completedFuture();
		return this.<Void>retryOperation(cb -> recordings().stop(name).execute(cb))
				// recording not found can happen if the recording was finished due to a hangup or sth
				.exceptionally(Futures.on(RecordingNotFoundException.class, t -> {
					logger.severe("Failed to stop recording - recording not found");
					return null;
				}))
				.thenAccept(v -> {
					logger.info("Record '" + name + "' stoped");
				})
				.exceptionally(Futures.on(RestException.class, e -> {
					logger.warning("Can't stop recording " + name + ": " + e);
					throw e;
				}))
				.thenCompose(v -> {
					wasCancelled .setRelease(true);
					// give some time for RecordingFinished event to be received
					Timers.schedule(() -> {
						waitUntilDone.completeExceptionally(new RecordingException(name, 
								"Stopping recording but timedout waiting for recording to finish"));
						}, 1500);
					return waitUntilDone;
				})
				.exceptionally(t -> { // probably stopping failed
					waitUntilDone.completeExceptionally(t);
					return null;
				});
	}

	/**
	 * true if talking in the channel was detected during the recording, false
	 * otherwise
	 *
	 * @return
	 */
	public boolean isTalkingDetect() {
		return wasTalkingDetected;
	}

	/**
	 * if the caller talked during the recording, get the duration of the talking
	 *
	 * @return
	 */
	public int getTalkingDuration() {
		return talkingDuration;
	}

	/**
	 * before recording, set what to do if the recording is already exists. default
	 * value is "overwrite"
	 *
	 * @param ifExist allowed values: fail, overwrite, append
	 * @return
	 */
	public Record setIfExist(String ifExist) {
		this.ifExists = ifExist;
		return this;
	}

	/**
	 * Read the recording start time
	 * @return time the recording started
	 */
	public LocalDateTime getRecordingStartTime() {
		return Objects.nonNull(recordingStartTime)
				? LocalDateTime.parse(recordingStartTime.toString(), DateTimeFormatter.ISO_INSTANT)
				: null;
	}

	public int getDuration() {
		return recording.getDuration(); 
	}
	
	@Override
	protected Exception tryIdentifyError(Throwable ariError) {
		return Objects.requireNonNullElseGet(super.tryIdentifyError(ariError), () -> {
			switch (Objects.requireNonNullElse(ariError.getMessage(), "")) {
			case "Recording not found": return new RecordingNotFoundException(name, ariError);
			}
			return null;
		});
	}
}

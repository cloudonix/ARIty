package io.cloudonix.arity;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.models.ChannelDtmfReceived;
import ch.loway.oss.ari4java.generated.models.ChannelTalkingStarted;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import ch.loway.oss.ari4java.generated.models.RecordingFinished;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.RecordingException;
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
	transient private LiveRecording recording;
	private CallController callController;
	private boolean isTermKeyWasPressed = false;
	private final static Logger logger = Logger.getLogger(Record.class.getName());
	transient private boolean wasTalkingDetected = false;
	private int talkingDuration = 0;
	private String ifExists = "overwrite"; // can be set to the following values: fail, overwrite, append
	private Instant recordingStartTime;
	private CompletableFuture<Void> waitUntilDone;
	private LinkedList<EventHandler<?>> eventHandlers = new LinkedList<>();

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
		if (beep) {
			logger.info("Play beep before recording");
			return callController.play("beep").run().thenAccept(res -> startRecording()).thenApply(res -> this);
		} else
			return startRecording().thenApply(res -> this);
	}

	/**
	 * start recording
	 *
	 */
	private CompletableFuture<Void> startRecording() {
		return Operation.<LiveRecording>retry(cb -> channels().record(getChannelId(), name, fileFormat)
				.setMaxDurationSeconds(maxDuration).setMaxSilenceSeconds(maxSilenceSeconds)
				.setIfExists(ifExists).setBeep(beep).execute(cb))
				.thenCompose(result -> {
					logger.info("Recording started! recording name is: " + name);
					recordingStartTime = Instant.now();
					Timers.schedule(this::stopRecording, TimeUnit.SECONDS.toMillis(maxDuration));
					setupHandlers();
					return Objects.requireNonNull(waitUntilDone, "Error setting up recording");
				})
				.exceptionally(Futures.on(RestException.class, e -> {
					throw new RecordingException(name, e);
				}));
	}

	private void setupHandlers() {
		waitUntilDone = new CompletableFuture<>();
		eventHandlers.addAll(Arrays.asList(
				// wait until Asterisk says we're done
				getArity().addEventHandler(RecordingFinished.class, getChannelId(), (record, se) -> {
					if (!Objects.equals(record.getRecording().getName(), name))
						return;
					long recordingEndTime = Instant.now().getEpochSecond();
					recording = record.getRecording();
					recording.setDuration(Integer.valueOf(
							String.valueOf(Math.abs(recordingEndTime - recordingStartTime.getEpochSecond()))));
					logger.info("Finished recording! recording duration is: "
							+ Math.abs(recordingEndTime - recordingStartTime.getEpochSecond()) + " seconds");
					if (wasTalkingDetected)
						talkingDuration = recording.getTalking_duration();
					cleanupHandlers();
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
	public LiveRecording getRecording() {
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
		return Objects.isNull(recording) ? "in-progress" : recording.getState();
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
		if (Objects.nonNull(recording)) {
			logger.fine("Recording has already stoped");
			return CompletableFuture.completedFuture(null);
		}
		return Operation.<Void>retry(cb -> recordings().stop(name).execute(cb))
				.thenAccept(v -> {
					logger.info("Record " + name + " stoped");
				})
				.exceptionally(Futures.on(RestException.class, e -> {
					logger.warning("Can't stop recording " + name + ": " + e);
					throw e;
				}));
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
	 * get recording as a file from asterisk recording folder
	 * @return absolute local file path on the Asterisk server, or null if recording has not finished yet
	 */
	public File getRecordAsFile() {
		if (Objects.isNull(recording))
			return null;
		return new File("/var/spool/asterisk/recording/" + recording.getName() + "." + recording.getFormat());
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
}

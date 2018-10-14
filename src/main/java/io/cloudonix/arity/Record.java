package io.cloudonix.arity;

import java.io.File;
import java.time.Instant;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;
import ch.loway.oss.ari4java.generated.ChannelTalkingStarted;
import ch.loway.oss.ari4java.generated.LiveRecording;
import ch.loway.oss.ari4java.generated.RecordingFinished;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.RecordingException;
import io.cloudonix.future.helper.FutureHelper;

public class Record extends CancelableOperations {

	private String name;
	private String fileFormat = "ulaw";
	private int maxDuration; // maximum duration of the recording
	private int maxSilenceSeconds;
	private boolean beep;
	private String terminateOnKey;
	private LiveRecording recording;
	private CallController callController;
	private boolean isTermKeyWasPressed = false;
	private final static Logger logger = Logger.getLogger(Record.class.getName());
	private CompletableFuture<LiveRecording> liveRecFuture = new CompletableFuture<LiveRecording>();
	private boolean wasTalkingDetect = false;
	private int talkingDuration = 0;
	private String ifExists = "overwrite"; // can be set to the following values: fail, overwrite, append

	/**
	 * Constructor
	 */

	public Record(CallController callController, String name, String fileFormat, int maxDuration, int maxSilenceSeconds,
			boolean beep, String terminateOnKey) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.name = name;
		this.fileFormat = (Objects.nonNull(fileFormat) && !Objects.equals(fileFormat, "")) ? fileFormat
				: this.fileFormat;
		this.maxDuration = maxDuration;
		this.maxSilenceSeconds = maxSilenceSeconds;
		this.beep = beep;
		this.terminateOnKey = terminateOnKey;
		callController.setTalkingInChannel("set", ""); // Enable talking detection in the channel
		if (beep)
			this.callController = callController;
	}

	@Override
	public CompletableFuture<Record> run() {
		if (beep) {
			logger.info("Play beep before recording");
			callController.play("beep").run().thenAccept(res -> startRecording());
		} else
			startRecording();
		return liveRecFuture.thenApply(res -> this);
	}

	/**
	 * start recording
	 * 
	 */
	private void startRecording() {
		getAri().channels().record(getChannelId(), name, fileFormat, maxDuration, maxSilenceSeconds, ifExists, beep,
				terminateOnKey, new AriCallback<LiveRecording>() {
					@Override
					public void onSuccess(LiveRecording result) {
						if (!(result instanceof LiveRecording))
							return;
						logger.info("Recording started! recording name is: " + name);
						long recordingStartTime = Instant.now().getEpochSecond();
						Timer timer = new Timer("Timer");
						TimerTask task = new TimerTask() {
							@Override
							public void run() {
								stopRecording();
							}
						};
						timer.schedule(task, TimeUnit.SECONDS.toMillis(Long.valueOf(Integer.toString(maxDuration))));

						getArity().addFutureEvent(RecordingFinished.class, getChannelId(), (record) -> {
							if (!Objects.equals(record.getRecording().getName(), name))
								return false;
							long recordingEndTime = Instant.now().getEpochSecond();
							recording = result;
							recording.setDuration(
									Integer.valueOf(String.valueOf(Math.abs(recordingEndTime - recordingStartTime))));
							logger.info("Finished recording! recording duration is: "
									+ Math.abs(recordingEndTime - recordingStartTime) + " seconds");
							if (wasTalkingDetect)
								talkingDuration = recording.getTalking_duration();
							liveRecFuture.complete(record.getRecording());
							return true;
						}, true);

						// Recognise if Talking was detected during the recording
						getArity().addFutureEvent(ChannelTalkingStarted.class, getChannelId(), talkStarted -> {
							if (Objects.equals(talkStarted.getChannel().getId(), getChannelId())) {
								logger.info("Recognised tallking in the channel");
								wasTalkingDetect = true;
								return true;
							}
							return false;
						}, true);

						// stop the recording by pressing the terminating key
						getArity().addFutureEvent(ChannelDtmfReceived.class, getChannelId(), dtmf -> {
							if (dtmf.getDigit().equals(terminateOnKey)) {
								logger.info("Terminating key was pressed, stop recording");
								isTermKeyWasPressed = true;
								timer.cancel();
								return true;
							}
							return false;
						}, false);

					}

					@Override
					public void onFailure(RestException e) {
						liveRecFuture.completeExceptionally(new RecordingException(name, e));
					}
				});
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
	 * @param name
	 *            name of the recording
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
		return recording.getState();
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
	CompletableFuture<Void> cancel() {
		if (Objects.nonNull(recording)) {
			logger.fine("Recording has already stoped");
			return FutureHelper.completedFuture();
		}
		CompletableFuture<Void> recordFuture = new CompletableFuture<Void>();
		getAri().recordings().stop(name, new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.info("Record " + name + " stoped");
				recordFuture.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("Can't stop recording " + name + ": " + e);
				recordFuture.completeExceptionally(e);
			}
		});
		return recordFuture;
	}

	/**
	 * true if talking in the channel was detected during the recording, false
	 * otherwise
	 * 
	 * @return
	 */
	public boolean isWasTalkingDetect() {
		return wasTalkingDetect;
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
	 * @param ifExist
	 *            allowed values: fail, overwrite, append
	 * @return
	 */
	public Record setIfExist(String ifExist) {
		this.ifExists = ifExist;
		return this;
	}

	/**
	 * get recording as a file from asterisk recording folder
	 * 
	 * @return
	 */
	public File getRecordAsFile() {
		File recFile = new File("/var/spool/asterisk/recording/" + recording.getName() + "." + recording.getFormat());
		return (recFile.exists()) ? recFile : null;
	}
}

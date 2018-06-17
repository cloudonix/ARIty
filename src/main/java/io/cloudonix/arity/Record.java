package io.cloudonix.arity;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;
import ch.loway.oss.ari4java.generated.LiveRecording;
import ch.loway.oss.ari4java.generated.RecordingFinished;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.RecordingException;

public class Record extends Operation {

	private String name;
	private String fileFormat;
	private int maxDuration = 1;
	private int maxSilenceSeconds = 0;
	private boolean beep = false;
	private String terminateOnKey = "";
	private LiveRecording recording;
	private CallController callController;
	private boolean isTermKeyWasPressed = false;
	private final static Logger logger = Logger.getLogger(Record.class.getName());
	private CompletableFuture<LiveRecording> liveRecFuture = new CompletableFuture<LiveRecording>();

	/**
	 * constructor with default values
	 * 
	 * @param callController
	 * @param name
	 * @param fileFormat
	 */
	public Record(CallController callController, String name, String fileFormat) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.name = name;
		this.fileFormat = fileFormat;
	}

	/**
	 * constructor with extended settings for the recording
	 */

	public Record(CallController callController, String name, String fileFormat, int maxDuration, int maxSilenceSeconds,
			boolean beep, String terminateOnKey) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.name = name;
		this.fileFormat = fileFormat;
		this.maxDuration = maxDuration;
		this.maxSilenceSeconds = maxSilenceSeconds;
		this.beep = beep;
		this.terminateOnKey = terminateOnKey;
		if (beep)
			this.callController = callController;
	}

	@Override
	public CompletableFuture<Record> run() {
		if (beep) {
			logger.info("play beep before recording");
			callController.play("beep.alaw").run().thenAccept(res -> startRecording());
		} else
			startRecording();
		return liveRecFuture.thenApply(res -> this);
	}

	/**
	 * start recording
	 * 
	 * @param liveRecFuture
	 */
	private void startRecording() {
		getAri().channels().record(getChannelId(), name, fileFormat, maxDuration, maxSilenceSeconds, "overwrite", beep,
				terminateOnKey, new AriCallback<LiveRecording>() {

					@Override
					public void onSuccess(LiveRecording result) {
						if (!(result instanceof LiveRecording))
							return;
						recording = result;
						logger.info("recording started! recording name: " + name);
						Timer timer = new Timer("Timer");

						getArity().addFutureEvent(RecordingFinished.class, (record) -> {
							if (!Objects.equals(record.getRecording().getName(), name))
								return false;
							recording = result;
							liveRecFuture.complete(record.getRecording());
							return true;
						});

						getArity().addFutureEvent(ChannelDtmfReceived.class, dtmf -> {
							if (dtmf.getDigit().equals(terminateOnKey)) {
								logger.info("terminating key was pressed, stop recording");
								isTermKeyWasPressed = true;
								timer.cancel();
								return true;
							}
							return false;
						} );

						TimerTask task = new TimerTask() {
							@Override
							public void run() {
								stopRecording();
							}
						};
						timer.schedule(task, TimeUnit.SECONDS.toMillis(Long.valueOf(Integer.toString(maxDuration))));
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

	public void stopRecording() {
		try {
			getAri().recordings().stop(name);
			logger.info("record " + name + " stoped");
		} catch (RestException e) {
			logger.warning("can't stop recording " + name);
		}
	}

}

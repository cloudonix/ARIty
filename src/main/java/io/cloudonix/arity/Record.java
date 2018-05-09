package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import ch.loway.oss.ari4java.generated.LiveRecording;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.RecordingFinished_impl_ari_2_0_0;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ErrorStream;
import io.cloudonix.arity.errors.RecordingException;


public class Record extends Operation {

	private String name;
	private String fileFormat;
	private int maxDuration = 0;
	private int maxSilenceSeconds = 0;
	private boolean beep = false;
	private String terminateOnKey = "";
	private LiveRecording recording;
	private final static Logger logger = Logger.getLogger(Record.class.getName());

	/**
	 * constructor with default values
	 * 
	 */
	public Record(CallController callController,  String name, String fileFormat) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.name = name;
		this.fileFormat = fileFormat;
	}
	
	/**
	 * constructor with extended settings for the recording
	 * 
	 */
	
	public Record(CallController callController, String name, String fileFormat, int maxDuration, int maxSilenceSeconds, boolean beep, String terminateOnKey) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.name = name;
		this.fileFormat = fileFormat;
		this.maxDuration = maxDuration;
		this.maxSilenceSeconds = maxSilenceSeconds;
		this.beep = beep;
		this.terminateOnKey = terminateOnKey;
	}


	@Override
	public CompletableFuture<? extends Operation> run() {
		CompletableFuture<LiveRecording> liveRecFuture = new CompletableFuture<LiveRecording>();
		
		getAri().channels().record(getChannelId(), name, fileFormat, maxDuration, maxSilenceSeconds, "overwrite", beep, terminateOnKey, new AriCallback<LiveRecording>() {

			@Override
			public void onSuccess(LiveRecording result) {
				if (!(result instanceof LiveRecording))
					return;
				recording = result;
				logger.info("recording started! recording name: "+ name);
				
				getArity().addFutureEvent(RecordingFinished_impl_ari_2_0_0.class, (record)->{
					if(!Objects.equals(record.getRecording().getName(), name))
						return false;
					recording = result;
					liveRecFuture.complete(record.getRecording());
					return true;
				});
				// wait x seconds and then hangup the call
				try {
					Thread.sleep(TimeUnit.SECONDS.toMillis(maxDuration));
					getAri().recordings().stop(name);
					logger.info("record "+name+" stoped");
				} catch (InterruptedException | RestException e) {
					logger.info("can't stop recording: " +ErrorStream.fromThrowable(e));
				}
			}
			
			@Override
			public void onFailure(RestException e) {
				liveRecFuture.completeExceptionally(new RecordingException(name, e));
			}
		});
		return liveRecFuture.thenApply(res->this);
	}
	
	/**
	 * get the recording
	 * @return
	 */
	public LiveRecording getRecording () {
		return recording;
	}
	
	/**
	 * get the name of the recording
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * set the name of the recording
	 * @param name name of the recording
	 */
	public void setName(String name) {
		this.name = name;
	}

}

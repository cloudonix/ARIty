package io.cloudonix.arity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.PlaybackException;

public class Play extends CancelableOperations{
	
	private StasisStart callStasisStart;
	private String fileLocation;
	private int timesToPlay = 1;
	private String uriScheme = "sound:";
	private Playback playback;

	private final static Logger logger = Logger.getLogger(Play.class.getName());
	private CompletableFuture<Play> compFuturePlayback;

	/**
	 * constructor 
	 * @param call
	 */
	public Play(Call call) {
		super(call.getChannelID(), call.getService(), call.getAri());
		callStasisStart = call.getCallStasisStart();
		compFuturePlayback = new CompletableFuture<>();
	}
	
	/**
	 * second constructor- will be used when we cancel the use of runSound and runRecording to general "run" and use enums to differ between
	 * the types of uri scheme
	 * @param call
	 * @param fileLocation
	 * @param times
	 */
	public Play(Call call, String fileLocation) {
		
		super(call.getChannelID(), call.getService(), call.getAri());
		callStasisStart = call.getCallStasisStart();
		this.fileLocation = fileLocation;
		compFuturePlayback = new CompletableFuture<>();
	}
	
	/**
	 * The method changes the uri scheme to recording and plays the stored recored
	 * 
	 * @param recLocation
	 * @return
	 */
	public CompletableFuture<Play> playRecording() {
		uriScheme = "recording:";	
		return run();
	}
	
	/**
	 * The method plays a playback of a specific ARI channel
	 * 
	 * @return
	 */
	public CompletableFuture<Play> run() {
		
		// create a "local" completable future in order to connect it to the global completable future. we want the local to end before starting a new one
		CompletableFuture<Playback> compPlaybackItr = new CompletableFuture<Playback> ();
		// create a unique UUID for the playback
		String pbID = UUID.randomUUID().toString();
		// "sound:hello-world";
		String fullPath = uriScheme + fileLocation;

		getAri().channels().play(getChanneLID(), fullPath, callStasisStart.getChannel().getLanguage(), 0, 0, pbID,
				new AriCallback<Playback>() {

					@Override
					public void onFailure(RestException e) {
						logger.warning("failed in playing playback " + e.getMessage());
						compFuturePlayback.completeExceptionally(new PlaybackException(fullPath, e));
					}

					@Override
					public void onSuccess(Playback resultM) {

						if (!(resultM instanceof Playback))
							return;

						logger.info("playback started! playback id: " + resultM.getId() + " type of playback: "
								+ uriScheme);
						// save the playback
						playback = resultM;

						// add a playback finished future event to the futureEvent list
						getService().addFutureEvent(PlaybackFinished.class, (playb) -> {
							if (!(playb.getPlayback().getId().equals(pbID)))
								return false;
							logger.info("playbackFinished and same playback id. Id is: " + pbID);
							// if it is play back finished with the same id, handle it here
							compPlaybackItr.complete(playb.getPlayback());
							return true;

							/*
							 * //add record finished service.addFutureEvent(RecordingFinished.class, (rec)
							 * -> { if(!(rec.getRecording().getName().equals(fileLocation))) return false;
							 * logger.info("record with name:" + rec.getRecording().getName() +
							 * " was finished"); });
							 */

						});
						logger.info("future event of playbackFinished was added");

					}

				});
		
		if(timesToPlay > 1) {
			timesToPlay = timesToPlay -1 ;
			return compPlaybackItr.thenCompose(x -> run());
		}
		
		return compPlaybackItr.thenCompose(pb->{ 
			compFuturePlayback.complete(this);
			return compFuturePlayback;
		});
	}
	
	/**
	 * set how many times to play the playback
	 * @param times
	 * @return
	 */
	public Play loop (int times) {
		this.timesToPlay = times;
		return this;		
	}
	
	public Playback getPlayback () {
		return playback;
	}

	@Override
	void cancel() {
		try {
			getAri().playbacks().stop(playback.getId());
		} catch (RestException e) {
			logger.info("failed in stopping the playback. Playback id is : " + playback.getId());
			new PlaybackException (playback.getId(),e);
		}
		logger.info("playback canceled. Playback id: " + playback.getId());
	}
	

}

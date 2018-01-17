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

public class Play extends Verb{
	
	private StasisStart callStasisStart;
	private String fileLocation;
	private int timesToPlay = 1;
	private String uriScheme = "sound:";

	private final static Logger logger = Logger.getLogger(Play.class.getName());
	private CompletableFuture<Playback> compFuturePlaybcak;

	/**
	 * constructor 
	 * @param call
	 */
	public Play(Call call) {
		super(call.getChannelID(), call.getService(), call.getAri());
		callStasisStart = call.getCallStasisStart();
		compFuturePlaybcak = new CompletableFuture<>();
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
		compFuturePlaybcak = new CompletableFuture<>();
	}
	
	/**
	 * Plays a sound playback a few times
	 * 
	 * @param times
	 *            - the number of repetitions of the playback
	 * @return
	 */	
	public CompletableFuture<Playback> run(int times) {

		if (times == 1) {
			return run();
		}

		return run(times - 1).thenCompose(x -> run());
	}
	
	/**
	 * The method changes the uri scheme to recording and plays the stored recored
	 * 
	 * @param recLocation
	 * @return
	 */
	public CompletableFuture<Playback> playRecording() {
		uriScheme = "recording:";	
		return run();
	}
	
	/**
	 * The method plays a playback of a specific ARI channel
	 * 
	 * @return
	 */
	public CompletableFuture<Playback> run() {
		
		if(timesToPlay > 1) {
			timesToPlay = timesToPlay -1 ;
			return run().thenCompose(x -> run());
		}
		
		// create a unique UUID for the playback
		String pbID = UUID.randomUUID().toString();
		logger.info("UUID: " + pbID);
		// "sound:hello-world";
		String fullPath = uriScheme + fileLocation;

		getAri().channels().play(getChanneLID(), fullPath, callStasisStart.getChannel().getLanguage(), 0, 0, pbID,
				new AriCallback<Playback>() {

					@Override
					public void onFailure(RestException e) {
						logger.warning("failed in playing playback " + e.getMessage());
						compFuturePlaybcak.completeExceptionally(new PlaybackException(fullPath, e));
					}

					@Override
					public void onSuccess(Playback resultM) {

						if (!(resultM instanceof Playback))
							return;

						logger.info("playback started! playback id: " + resultM.getId() + " type of playback: "
								+ uriScheme);

						// add a playback finished future event to the futureEvent list
						getService().addFutureEvent(PlaybackFinished.class, (playb) -> {
							if (!(playb.getPlayback().getId().equals(pbID)))
								return false;
							logger.info("playbackFinished and same playback id. Id is: " + pbID);
							// if it is play back finished with the same id, handle it here
							compFuturePlaybcak.complete(playb.getPlayback());
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

		return compFuturePlaybcak;
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

}

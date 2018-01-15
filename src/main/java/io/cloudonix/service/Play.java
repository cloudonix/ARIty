package io.cloudonix.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.service.errors.PlaybackException;

public class Play  extends Verb{
	
	private CompletableFuture<Playback> cf;
	private StasisStart callStasisStart;

	private final static Logger logger = Logger.getLogger(HangUp.class.getName());
	
	/**
	 * constructor 
	 * @param call
	 */
	public Play(Call call) {
		super(call.getChannelID(), call.getService(), call.getAri());
		cf = new CompletableFuture<>();
		callStasisStart = call.getCallStasisStart();
	}
	
	/**
	 * Plays a sound playback a few times
	 * 
	 * @param times
	 *            - the number of repetitions of the playback
	 * @return
	 */	
	public CompletableFuture<Playback> run(int times, String uriScheme, String fileLocation) {

		if (times == 1) {
			return run(uriScheme, fileLocation);
		}

		return run(times - 1, uriScheme, fileLocation).thenCompose(x -> run(uriScheme, fileLocation));
	}
	
	/**
	 * play the relevant sound few times
	 * 
	 * @param times-
	 *            how many times to play the sound
	 * @param soundLocation
	 * @return
	 */
	public CompletableFuture<Playback> runSound(int times, String soundLocation) {
		return run(times, "sound:", soundLocation);
	}
	

	/**
	 * The method plays the stored recored
	 * 
	 * @param recLocation
	 * @return
	 */
	public CompletableFuture<Playback> playRecording(String recLocation) {
		return run("recording:", recLocation);
	}
	
	/**
	 * The method plays a playback of a specific ARI channel
	 * 
	 * @return
	 */
	public CompletableFuture<Playback> run(String uriScheme, String fileLocation) {

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
						cf.completeExceptionally(new PlaybackException(fullPath, e));
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
							cf.complete(playb.getPlayback());
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

		return cf;
	}

}

package io.cloudonix.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.RecordingFailed;
import ch.loway.oss.ari4java.generated.RecordingFinished;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.service.errors.AnswerCallException;
import io.cloudonix.service.errors.HangUpException;
import io.cloudonix.service.errors.PlaybackException;

/**
 * The class represents an incoming call
 * 
 * @author naamag
 *
 */
public class Call {

	private StasisStart callStasisStart;
	private ARI ari;
	private String channelID;
	private Service service;

	private final static Logger logger = Logger.getLogger(Call.class.getName());

	public Call(StasisStart ss, ARI a, Service service) {

		callStasisStart = ss;
		channelID = ss.getChannel().getId();
		ari = a;
		this.service = service;
	}

	/**
	 * The method executes the application
	 * 
	 * @param voiceApp
	 */
	public void executeVoiceApp(Runnable voiceApp) {
		voiceApp.run();
	}

	/**
	 * The method plays a sound playback of a specific ARI channel
	 * 
	 * @return
	 */
	public CompletableFuture<Playback> play(String uriScheme, String fileLocation) {

		CompletableFuture<Playback> res = new CompletableFuture<Playback>();
		// create a unique UUID for the playback
		String pbID = UUID.randomUUID().toString();
		logger.info("UUID: " + pbID);
		// "sound:hello-world";
		String fullPath = uriScheme + fileLocation;

		ari.channels().play(channelID, fullPath, callStasisStart.getChannel().getLanguage(), 0, 0, pbID,
				new AriCallback<Playback>() {

					@Override
					public void onFailure(RestException e) {
						logger.warning("failed in playing playback " + e.getMessage());
						res.completeExceptionally(new PlaybackException(fullPath, e));
					}

					@Override
					public void onSuccess(Playback resultM) {

						if (!(resultM instanceof Playback))
							return;

						logger.info("playback started! playback id: " + resultM.getId() + " type of playback: "
								+ uriScheme);

						// add a playback finished future event to the futureEvent list
						service.addFutureEvent(PlaybackFinished.class, (playb) -> {
							if (!(playb.getPlayback().getId().equals(pbID)))
								return false;
							logger.info("playbackFinished and same playback id. Id is: " + pbID);
							// if it is play back finished with the same id, handle it here
							res.complete(playb.getPlayback());
							return true;
							
						/*//add record finished
						service.addFutureEvent(RecordingFinished.class, (rec) -> {
							if(!(rec.getRecording().getName().equals(fileLocation)))
								return false;
							logger.info("record with name:" + rec.getRecording().getName() + " was finished");
						});*/

						});
						logger.info("future event of playbackFinished was added");

					}

				});

		return res;
	}

	/**
	 * Plays a sound playback a few times
	 * 
	 * @param times
	 *            - the number of repetitions of the playback
	 * @return
	 */

	public CompletableFuture<Playback> play(int times, String uriScheme, String fileLocation) {

		if (times == 1) {
			return play(uriScheme, fileLocation);
		}

		return play(times - 1, uriScheme, fileLocation).thenCompose(x -> play(uriScheme, fileLocation));
	}

	/**
	 * play the relevant sound few times
	 * 
	 * @param times-
	 *            how many times to play the sound
	 * @param soundLocation
	 * @return
	 */
	public CompletableFuture<Playback> playSound(int times, String soundLocation) {
		return play(times, "sound:", soundLocation);
	}
	
	/**
	 * The method plays the stored recored
	 * @param recLocation
	 * @return
	 */
	public CompletableFuture<Playback> playRecording(String recLocation) {
		return play("recording:", recLocation);
	}

	/**
	 * The method hang up a call
	 * 
	 * @return
	 */
	public CompletableFuture<Void> hangUpCall(){
		CompletableFuture<Void> cf = new CompletableFuture<>();
		
		try {
			// hang up the call
			ari.channels().hangup(channelID, "normal");
		} catch (RestException e) {
			logger.severe("failed hang up the call");
			cf.completeExceptionally(new HangUpException (e));
			return cf;
		}
		return CompletableFuture.completedFuture(null);
	}

	/**
	 * The method answer a call that was received from an ARI channel
	 * 
	 * @param ari
	 * @param ss
	 * @return
	 * @throws AnswerCallException
	 */
	public CompletableFuture<Void> answer() {
		CompletableFuture<Void> cf = new CompletableFuture<>();
		
		try {
			// answer the call
			ari.channels().answer(channelID);
		} catch (RestException e) {
			logger.severe("failed in answering the call: " + e);
			 cf.completeExceptionally(new AnswerCallException (e));
			 return cf;
		}

		logger.info("call answered");
		
		return CompletableFuture.completedFuture(null);
	}

	/*public CompletableFuture<Void> record(String recFileName) throws RecordingException {
		
		try {
			//add recording
			recording = ari.channels().record(channelID, recFileName, "wav", 6, 1, "overwrite", true, "#");
		} catch (RestException e) {
			logger.warning("failed in recording: "+ e.getMessage());
			throw new RecordingException (e);
		}
		
		logger.info("record started");

		return CompletableFuture.completedFuture(null);

	}*/

	@Override
	public String toString() {
		// String SSstatus = this.callStasisStart.equals(null) ? "doesn't exist" :
		// "exists";
		return " Channel id of the call:" + this.channelID + " App Name:" + this.service.getAppName();
	}

}

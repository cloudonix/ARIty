package io.cloudonix.ariAppService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.logicException.AnswerCallException;
import io.cloudonix.logicException.HangUpException;
import io.cloudonix.logicException.PlaybackException;
/**
 * The class represents an incoming call 
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
	 * @param voiceApp
	 */
	public void executeVoiceApp (Runnable voiceApp ) {
		voiceApp.run();
	}
	
	/**
	 * The method plays a playback of a specific ARI channel
	 * @return
	 */
	public CompletableFuture<Playback> play() {
		
		CompletableFuture<Playback> res = new CompletableFuture<Playback>();
		//create a unique UUID for the playback
		String pbID = UUID.randomUUID().toString();
		logger.info("UUID: " + pbID);

		ari.channels().play(channelID, "sound:hello-world", callStasisStart.getChannel().getLanguage(), 0, 0, pbID,
				new AriCallback<Playback>() {

					@Override
					public void onFailure(RestException e) {
						logger.warning("failed in playing playback 'hello-world' " + e.getMessage());
						res.completeExceptionally(new PlaybackException(e));
						
					}

					@Override
					public void onSuccess(Playback resultM) {

						if (!(resultM instanceof Playback))
							return;

						logger.info("playback started! playback id: " + resultM.getId());

						// add a playback finished future event to the futureEvent list
						service.addFutureEvent(PlaybackFinished.class, (playb) -> {
							if (!(playb.getPlayback().getId().equals(pbID)))
								return false;
							logger.info("playbackFinished and same playback id. Id is: " + pbID);
							// if it is play back finished with the same id, handle it here
							res.complete(playb.getPlayback());
							return true;

						});
						logger.info("future event of playback finished was added");
					}

				});

		return res;
	}
	
	/**
	 * Plays a playback a few times (according to times)
	 * @param times
	 * @return
	 */
	
	public CompletableFuture<Playback> play(int times) {
		
		if (times == 1) {
			return play();
		}
		
		return play(times - 1).thenCompose(x -> play());
	}

	/**
	 * The method hang up a call
	 * @return
	 */
	public CompletableFuture<Void> hangUpCall()throws HangUpException {
		try {
			ari.channels().hangup(channelID, "normal");
		} catch (RestException e) {
			logger.warning("failed to answer the call");
			throw new HangUpException(e);
			
			
		}

		return CompletableFuture.completedFuture(null);
	}
	
	/**
	 * The method answer a call that was received from an ARI channel 
	 * @param ari
	 * @param ss
	 * @return
	 */
	public CompletableFuture<Void> answer() {

		try {
			// answer the call
			ari.channels().answer(channelID);
		} catch (RestException e) {
			e.printStackTrace();
		}
		
		logger.info("call answered");
		return CompletableFuture.completedFuture(null);

	}
	
	@Override
	public String toString () {
		//String SSstatus = this.callStasisStart.equals(null) ? "doesn't exist" : "exists";
		return " Channel id of the call: " + this.channelID + " App Name: "+ this.service.getAppName() ;
	}

}

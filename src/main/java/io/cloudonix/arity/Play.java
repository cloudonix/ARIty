package io.cloudonix.arity;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.PlaybackException;

/**
 * The class represents a Play operation (plays a playback and cancels the
 * playback if needed)
 * 
 * @author naamag
 *
 */
public class Play extends CancelableOperations {

	private String language;
	private String playFileName;
	private int timesToPlay = 1;
	private String uriScheme = "sound:";
	private Playback playback;
	private final static Logger logger = Logger.getLogger(Play.class.getName());

	/**
	 * constructor
	 * 
	 * @param callController
	 *            call main logic object
	 * @param fileName
	 *            name of the file to be played
	 */
	public Play(CallController callController, String fileName) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		language = callController.getCallStasisStart().getChannel().getLanguage();
		this.playFileName = fileName;
	}

	/**
	 * The method changes the uri scheme to recording and plays the stored recored
	 * 
	 * @return
	 */
	public CompletableFuture<Play> playRecording() {
		setUriScheme("recording:");
		return run();
	}

	/**
	 * The method plays a playback of a specific ARI channel
	 * 
	 * @return
	 */
	public CompletableFuture<Play> run() {
		CompletableFuture<Playback> compPlaybackItr = new CompletableFuture<Playback>();
		if (timesToPlay != 0) {
			// create a unique UUID for the playback
			String playbackId = UUID.randomUUID().toString();
			String fullPath = getUriScheme() + playFileName;
			getAri().channels().play(getChannelId(), fullPath, language, 0, 0, playbackId, new AriCallback<Playback>() {
				@Override
				public void onFailure(RestException e) {
					logger.warning("Failed in playing playback " + e.getMessage());
					compPlaybackItr.completeExceptionally(new PlaybackException(fullPath, e));
				}

				@Override
				public void onSuccess(Playback resultM) {
					if (!(resultM instanceof Playback))
						return;
					playback = resultM;
					logger.info(
							"Playback started! Playing: " + playFileName + " and playback id is: " + playback.getId());

					getArity().addFutureEvent(PlaybackFinished.class, getChannelId(), (playb) -> {
						if (!(playb.getPlayback().getId().equals(playbackId)))
							return false;
						logger.info("PlaybackFinished id is the same as playback id.  ID is: " + playbackId);
						compPlaybackItr.complete(playb.getPlayback());
						return true;

					}, false);
					logger.info("Future event of playbackFinished was added");
				}
			});

			if (timesToPlay > 1) {
				timesToPlay = timesToPlay - 1;
				return compPlaybackItr.thenCompose(pb -> run());
			}
		}
		return compPlaybackItr.thenApply(pb -> this);
	}

	/**
	 * set how many times to play the play-back
	 * 
	 * @param times
	 * @return
	 */
	public Play loop(int times) {
		this.timesToPlay = times;
		return this;
	}

	/**
	 * Get the play-back
	 * 
	 * @return
	 */
	public Playback getPlayback() {
		return playback;
	}

	@Override
	CompletableFuture<Void> cancel() {
		timesToPlay = 0;
		if (Objects.isNull(playback))
			return CompletableFuture.completedFuture(null);
		logger.info("Trying to cancel a playback. Playback id: " + playback.getId());
		return this.<Void>toFuture(cb -> getAri().playbacks().stop(playback.getId(), cb))
				.thenAccept(pb -> logger.info("Playback canceled. Playback id: " + playback.getId()));
	}

	/**
	 * get the name of the file to play
	 * 
	 * @return
	 */
	public String getPlayFileName() {
		return playFileName;
	}

	/**
	 * set the file to be played
	 * 
	 * @param playFileName
	 */
	public void setPlayFileName(String playFileName) {
		this.playFileName = playFileName;
	}

	public String getUriScheme() {
		return uriScheme;
	}

	/**
	 * set the uri scheme before playing and get the update Play operation
	 * 
	 * @param uriScheme
	 * @return
	 */
	public Play setUriScheme(String uriScheme) {
		this.uriScheme = uriScheme;
		return this;
	}

	public String getLanguage() {
		return language;
	}

	/**
	 * set the playback language before playing
	 * 
	 * @param channelLanguage language to set
	 * @return
	 */
	public Play setLanguage(String channelLanguage) {
		this.language = channelLanguage;
		return this;
	}

}

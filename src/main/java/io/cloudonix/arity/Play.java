package io.cloudonix.arity;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.Playback;
import ch.loway.oss.ari4java.generated.models.PlaybackFinished;
import io.cloudonix.arity.errors.PlaybackException;

/**
 * The class represents a Play operation (plays a playback and cancels the
 * playback if needed)
 *
 * @author naamag
 *
 */
public class Play extends CancelableOperations {
	private final static Logger logger = Logger.getLogger(Play.class.getName());

	private String language="en";
	private String uriScheme = "sound";
	private String playFileName;
	private AtomicInteger timesToPlay = new AtomicInteger(1);
	private AtomicBoolean cancelled = new AtomicBoolean(false);
	private AtomicReference<Playback> playback = new AtomicReference<>();
	private volatile String currentPlaybackId;

	/**
	 * Play the specified content using the specified scheme (default "sound")
	 *
	 * @param callController controller for the channel
	 * @param filename       content to play
	 */
	public Play(CallController callController, String filename) {
		super(callController.getChannelId(), callController.getARIty());
		this.playFileName = filename;
		initLanguage(callController);
	}

	private void initLanguage(CallController callController) {
		Channel chan = callController.getChannel();
		if (Objects.nonNull(chan) && Objects.nonNull(chan.getLanguage())) {
			this.language = chan.getLanguage();
			return;
		}
		if (Objects.nonNull(callController.getCallState()) && Objects.nonNull(callController.getCallState().getChannel()))
			this.language = callController.getCallState().getChannel().getLanguage();
		else
			logger.fine("Using default language: "+language);
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
		logger.info("Play::run");
		String fullPath = uriScheme +":"+ playFileName;
		return startPlay(fullPath)
				.thenCompose(v -> {
					logger.info("startPlay finished");
					if (cancelled() || timesToPlay.decrementAndGet() <= 0)
						return CompletableFuture.completedFuture(this);
					return run();
				})
				.whenComplete((v,t) -> { logger.info("Play::run exiting"); });
	}

	protected CompletableFuture<Play> startPlay(String path) {
		if (cancelled()) // if we're already cancelled, make any additional iteration a no-op
			return CompletableFuture.completedFuture(null);

		currentPlaybackId = UUID.randomUUID().toString();
		CompletableFuture<Play> playbackFinished = new CompletableFuture<>();
		getArity().addEventHandler(PlaybackFinished.class, getChannelId(), (finished, se) -> {
			String finisheId = finished.getPlayback().getId();
			if (Objects.equals(finisheId, currentPlaybackId))
				return;
			currentPlaybackId = null;
			logger.info("Finished playback " + finisheId);
			playback.set(null);
			playbackFinished.complete(this);
			se.unregister();
		});
		
		return this.<Playback>retryOperation(h -> channels().play(getChannelId(), path).setLang(language).setPlaybackId(currentPlaybackId).execute(h))
		.thenCompose(playback -> {
			this.playback.set(playback); // store ongoing playback for cancelling
			logger.info("Playback started! Playing: " + playFileName + " and playback id is: " + playback.getId());
			return playbackFinished;
		})
		.exceptionally(e -> {
			logger.warning("Failed in playing playback " + e.getMessage());
			throw new CompletionException(new PlaybackException(path, e));
		});
	}

	/**
	 * set how many times to play the play-back
	 *
	 * @param times
	 * @return
	 */
	public Play loop(int times) {
		timesToPlay.set(times);
		return this;
	}

	/**
	 * Get the play-back
	 *
	 * @return
	 */
	public Playback getPlayback() {
		return playback.get();
	}

	@Override
	public CompletableFuture<Void> cancel() {
		cancelled.set(true);
		if (currentPlaybackId == null)
			return CompletableFuture.completedFuture(null); // no need to cancel, before startPlay is called again, cancelled() will be checked
		logger.info("Trying to cancel a playback. Playback id: " + currentPlaybackId);
		return this.<Void>retryOperation(cb -> playbacks().stop(currentPlaybackId).execute(cb))
				.thenAccept(pb -> logger.info("Playback canceled " + currentPlaybackId));
	}
	
	public boolean cancelled() {
		return cancelled.get();
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

package io.cloudonix.arity;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.IntStream;

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

	private String language="en";
	private String playFileName;
	private int timesToPlay = 1;
	private String uriScheme = "sound";
	private AtomicReference<Playback> playback = new AtomicReference<>();
	private AtomicBoolean cancelled = new AtomicBoolean(false);
	private final static Logger logger = Logger.getLogger(Play.class.getName());

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
		// create a unique UUID for the playback
		String fullPath = uriScheme +":"+ playFileName;

		return IntStream.range(0, timesToPlay)
				.mapToObj(i -> playOnce(fullPath))
				.reduce((a,b) -> (() -> a.get().thenCompose(v -> b.get())))
				.orElseGet(() -> (() -> CompletableFuture.<Void>completedFuture(null)))
				.get()
				.thenRun(() -> playback.setRelease(null))
				.thenApply(v -> this);
	}

	private Supplier<CompletableFuture<Void>> playOnce(String path) {
		if (cancelled.getAcquire()) // if we're already cancelled, make any additional iteration a no-op
			return () -> CompletableFuture.completedFuture(null);

		String playbackId = UUID.randomUUID().toString();
		return () -> this.<Playback>retryOperation(h -> channels()
				.play(getChannelId(), path).setLang(language).setOffsetms(0).setSkipms(0).setPlaybackId(playbackId).execute(h))
		.thenCompose(playback -> {
			this.playback.setRelease(playback); // store ongoing playback for cancelling
			logger.info("Playback started! Playing: " + playFileName + " and playback id is: " + playback.getId());

			// wait for PlaybackFinished event
			CompletableFuture<Void> playbackFinished = new CompletableFuture<>();
			getArity().addEventHandler(PlaybackFinished.class, getChannelId(), (finished, se) -> {
				if (!(finished.getPlayback().getId().equals(playbackId)))
					return;
				logger.info("Finished playback " + playbackId);
				playbackFinished.complete(null);
				se.unregister();
			});
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
		this.timesToPlay = times;
		return this;
	}

	/**
	 * Get the play-back
	 *
	 * @return
	 */
	public Playback getPlayback() {
		return playback.getAcquire();
	}

	@Override
	public CompletableFuture<Void> cancel() {
		cancelled.setRelease(true);
		Playback current = playback.getAndSet(null); // cancel is re-entrant
		if (Objects.isNull(current))
			return CompletableFuture.completedFuture(null);
		logger.info("Trying to cancel a playback. Playback id: " + current.getId());
		return this.<Void>retryOperation(cb -> playbacks().stop(current.getId()).execute(cb))
				.thenAccept(pb -> logger.info("Playback canceled " + current.getId()));
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

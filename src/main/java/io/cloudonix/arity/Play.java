package io.cloudonix.arity;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.models.Playback;
import ch.loway.oss.ari4java.generated.models.PlaybackFinished;
import io.cloudonix.arity.errors.ARItyException;
import io.cloudonix.arity.errors.ChannelNotFoundException;
import io.cloudonix.arity.errors.PlaybackException;
import io.cloudonix.arity.errors.PlaybackNotFoundException;
import io.cloudonix.arity.helpers.Futures;
import io.cloudonix.arity.models.AsteriskBridge;
import io.cloudonix.arity.models.AsteriskChannel;

/**
 * The class represents a Play operation (plays a playback and cancels the
 * playback if needed)
 *
 * @author naamag
 *
 */
public class Play extends CancelableOperations {
	private final static Logger logger = LoggerFactory.getLogger(Play.class);

	private String language="en";
	private String uriScheme = "sound";
	private String playFileName;
	private AtomicInteger timesToPlay = new AtomicInteger(1);
	private AtomicBoolean cancelled = new AtomicBoolean(false);
	private AtomicReference<Playback> playback = new AtomicReference<>();
	private volatile String currentPlaybackId;

	private AsteriskBridge playBridge;

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
		AsteriskChannel chan = callController.getChannel();
		if (Objects.nonNull(chan) && Objects.nonNull(chan.getLanguage())) {
			this.language = chan.getLanguage();
			return;
		}
		if (Objects.nonNull(callController.getCallState()) && Objects.nonNull(callController.getCallState().getChannel()))
			this.language = callController.getCallState().getChannel().getLanguage();
		else
			logger.debug("Using default language: {}", language);
	}
	
	public Play withBridge(AsteriskBridge bridge) {
		this.playBridge = bridge;
		return this;
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
		String fullPath = Stream.of(playFileName.split(",")).map(f -> {
			if (f.matches("^https?://.*")) // HTTP URL
				return "sound:" + f; // play as sound
			if (f.contains(":")) // URI with scheme
				return f; // play as is
			// otherwise, prefix with our set scheme
			return uriScheme + ":" + f;
		}).collect(Collectors.joining(","));
		logger.debug("Play::run ({})", fullPath);
		return startPlay(fullPath)
				.thenCompose(v -> {
					logger.info("{}|startPlay finished ({})", currentPlaybackId, fullPath);
					if (cancelled() || timesToPlay.decrementAndGet() <= 0)
						return CompletableFuture.completedFuture(this);
					return run();
				})
				.whenComplete((v,t) -> { logger.debug("{}|Play::run ({})", currentPlaybackId, fullPath); });
	}

	protected CompletableFuture<Play> startPlay(String path) {
		if (cancelled()) // if we're already cancelled, make any additional iteration a no-op
			return CompletableFuture.completedFuture(null);

		currentPlaybackId = UUID.randomUUID().toString();
		CompletableFuture<Play> playbackFinished = new CompletableFuture<>();
		getArity().addEventHandler(PlaybackFinished.class, getChannelId(), (finished, se) -> {
			String finishId = finished.getPlayback().getId();
			if (!Objects.equals(finishId, currentPlaybackId))
				return;
			logger.info("{}|Finished playback: {}", finishId, finished.getPlayback().getState());
			playback.set(null);
			playbackFinished.complete(this);
			se.unregister();
		});
		
		return executePlayOperation(path)
		.thenCompose(playback -> {
			this.playback.set(playback); // store ongoing playback for cancelling
			logger.info("{}|Playback started! Playing: {} and playback id is: {}", currentPlaybackId, playFileName, playback.getId());
			return playbackFinished;
		})
		.exceptionally(e -> {
			while (e instanceof RuntimeException && e.getCause() != null)
				e = e.getCause();
			if (e instanceof ChannelNotFoundException)
				throw new CompletionException(e);
			logger.warn("Failed in playing playback", e);
			throw new CompletionException(new PlaybackException(path, e));
		});
	}

	private CompletableFuture<Playback> executePlayOperation(String path) {
		if (this.playBridge != null)
			return this.retryOperation(h -> bridges().play(this.playBridge.getId(), path).setLang(language).setPlaybackId(currentPlaybackId).execute(h));
		return this.retryOperation(h -> channels().play(getChannelId(), path).setLang(language).setPlaybackId(currentPlaybackId).execute(h));
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
		if (cancelled.compareAndExchange(false, true) != false || currentPlaybackId == null)
			// if double canceling, or canceling before we generate the ID on start, we're done here
			return CompletableFuture.completedFuture(null); // start() will check if it was cancelled
		logger.info("{}|Trying to cancel a playback", currentPlaybackId);
		return Operation.<Void>retry(cb -> playbacks().stop(currentPlaybackId).execute(cb), ARItyException::ariRestExceptionMapper)
				.thenAccept(pb -> logger.info("{}|Playback canceled", currentPlaybackId))
				.exceptionally(Futures.on(PlaybackNotFoundException.class, e -> {
					if (playback.get() != null) // don't complain if playback has already successfully ended
						logger.warn("Playback {} was not found during cancel - ignoring", currentPlaybackId);
					return null;
				}));
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

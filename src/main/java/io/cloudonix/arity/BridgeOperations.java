package io.cloudonix.arity;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.LiveRecording;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.RecordingFinished;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ErrorStream;

/**
 * The class handles all bridge operations
 * 
 * @author naamag
 *
 */
public class BridgeOperations {

	private ARIty arity;
	private final static Logger logger = Logger.getLogger(ARIty.class.getName());
	private String bridgeId;
	private String recordingName;
	private String recordFormat = "wav";
	private int maxDurationSeconds = 0;
	private int maxSilenceSeconds = 0;
	private String ifExists = "overwrite";
	private boolean beep = false;
	private String terminateOn = "#";
	private HashMap<String, LiveRecording> recordings = new HashMap<>();

	/**
	 * Constructor
	 * 
	 * @param arity
	 *            instance of ARIty
	 */
	public BridgeOperations(ARIty arity) {
		this.arity = arity;
		this.bridgeId = UUID.randomUUID().toString();
	}

	/**
	 * Constructor with more options
	 * 
	 * @param arity
	 *            instance of ARIty
	 * @param recordFormat
	 *            Format to encode audio in
	 * @param maxDurationSeconds
	 *            Maximum duration of the recording, in seconds. 0 for no limit
	 * @param maxSilenceSeconds
	 *            Maximum duration of silence, in seconds. 0 for no limit
	 * @param ifExists
	 *            Action to take if a recording with the same name already exists.
	 *            Allowed values: fail, overwrite, append
	 * @param beep
	 *            true if need to play beep when recording begins, false otherwise
	 * @param terminateOn
	 *            DTMF input to terminate recording. Allowed values: none, any, *, #
	 */
	public BridgeOperations(ARIty arity, String recordFormat, int maxDurationSeconds, int maxSilenceSeconds,
			String ifExists, boolean beep, String terminateOn) {
		this.arity = arity;
		this.recordFormat = recordFormat;
		this.maxDurationSeconds = maxDurationSeconds;
		this.maxSilenceSeconds = maxSilenceSeconds;
		this.ifExists = ifExists;
		this.beep = beep;
		this.terminateOn = terminateOn;
	}

	/**
	 * Create a new bridge
	 * 
	 * @return
	 */
	public CompletableFuture<Bridge> createBridge() {
		CompletableFuture<Bridge> future = new CompletableFuture<Bridge>();

		arity.getAri().bridges().create("mixing", bridgeId, "dialBridge", new AriCallback<Bridge>() {
			@Override
			public void onSuccess(Bridge result) {
				logger.info("Dial bridge was created");
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.severe("Failed creating dial bridge: " + ErrorStream.fromThrowable(e));
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	/**
	 * Shut down this bridge
	 * 
	 * @return
	 */
	public CompletableFuture<Void> destroyBridge() {
		CompletableFuture<Void> future = new CompletableFuture<Void>();

		arity.getAri().bridges().destroy(bridgeId, new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.info("Bridge was destroyed successfully. Bridge id: " + bridgeId);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.severe("Failed destroying brdige with id: " + bridgeId + " :" + ErrorStream.fromThrowable(e));
				future.completeExceptionally(e);
			}
		});
		recordings.clear();
		return future;
	}

	/**
	 * add new channel to this bridge
	 * 
	 * @param channelId
	 *            id of the channel to add the bridge
	 * @return
	 */
	public CompletableFuture<Void> addChannelToBridge(String channelId) {
		CompletableFuture<Void> future = new CompletableFuture<Void>();
		arity.getAri().bridges().addChannel(bridgeId, channelId, "member", new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.info("Channel with id: " + channelId + " was added to bridge: " + bridgeId);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.severe("Failed to connect channel to the bridge: " + ErrorStream.fromThrowable(e));
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	/**
	 * remove channel from the bridge
	 * 
	 * @param channelId
	 *            id of the channel to remove from the bridge
	 * @return
	 */
	public CompletableFuture<Void> removeChannelFromBridge(String channelId) {
		CompletableFuture<Void> future = new CompletableFuture<Void>();
		arity.getAri().bridges().removeChannel(bridgeId, channelId, new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.info("Channel with id: " + channelId + " was removed to bridge: " + bridgeId);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.severe("Failed to remove channel from the bridge: " + ErrorStream.fromThrowable(e));
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	/**
	 * Play media file to the bridge
	 * 
	 * @param fileToPlay
	 *            name of the file to be played
	 * @return
	 */
	public CompletableFuture<Playback> playMediaToBridge(String fileToPlay) {
		CompletableFuture<Playback> future = new CompletableFuture<Playback>();
		String playbackId = UUID.randomUUID().toString();

		arity.getAri().bridges().play(bridgeId, "sound:" + fileToPlay, "en", 0, 0, playbackId,
				new AriCallback<Playback>() {

					@Override
					public void onSuccess(Playback result) {
						logger.fine("playing: " + fileToPlay);
						arity.addFutureEvent(PlaybackFinished.class, bridgeId, (pbf) -> {
							if (!(pbf.getPlayback().getId().equals(playbackId)))
								return false;
							logger.fine("PlaybackFinished id is the same as playback id.  ID is: " + playbackId);
							future.complete(pbf.getPlayback());
							return true;
						}, false);
						logger.fine("Future event of playbackFinished was added");
						future.complete(result);
					}

					@Override
					public void onFailure(RestException e) {
						logger.info("Failed playing file " + fileToPlay + " : " + ErrorStream.fromThrowable(e));
						future.completeExceptionally(e);
					}
				});
		return future;
	}

	/**
	 * play music on hold to the bridge
	 * 
	 * @param holdMusicFile
	 *            file of the music to be played while holding ("" for default
	 *            music)
	 * @return
	 */
	public CompletableFuture<Void> startMusicOnHold(String holdMusicFile) {
		CompletableFuture<Void> future = new CompletableFuture<Void>();
		String fileToPlay = (Objects.equals(holdMusicFile, "")) ? "sound:pls-hold-while-try" : holdMusicFile;
		arity.getAri().bridges().startMoh(bridgeId, fileToPlay, new AriCallback<Void>() {

			@Override
			public void onSuccess(Void result) {
				logger.info("Playing music on hold to bridge: " + bridgeId);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.info("Failed playing music on hold to bridge: " + ErrorStream.fromThrowable(e));
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	/**
	 * stop playing music on hold to the bridge
	 * 
	 * @return
	 */
	public CompletableFuture<Void> stopMusicOnHold() {
		CompletableFuture<Void> future = new CompletableFuture<Void>();
		arity.getAri().bridges().stopMoh(bridgeId, new AriCallback<Void>() {

			@Override
			public void onSuccess(Void result) {
				logger.info("Playing music on hold to bridge: " + bridgeId);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.info("Failed playing music on hold to bridge: " + ErrorStream.fromThrowable(e));
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	/**
	 * record the mixed audio from all channels participating in this bridge.
	 * 
	 * @return
	 */
	public CompletableFuture<LiveRecording> recordBridge() {
		CompletableFuture<LiveRecording> future = new CompletableFuture<LiveRecording>();
		recordingName = UUID.randomUUID().toString();
		arity.getAri().bridges().record(bridgeId, recordingName, recordFormat, maxDurationSeconds, maxSilenceSeconds,
				ifExists, beep, terminateOn, new AriCallback<LiveRecording>() {

					@Override
					public void onSuccess(LiveRecording result) {
						logger.info("Strated Recording bridge with id: " + bridgeId + " and recording name is: "
								+ recordingName);

						arity.addFutureEvent(RecordingFinished.class, bridgeId, (record) -> {
							if (!Objects.equals(record.getRecording().getName(), recordingName))
								return false;
							logger.info("Finished recording: " + recordingName);
							recordings.put(recordingName, result);
							future.complete(record.getRecording());
							return true;
						}, true);
					}

					@Override
					public void onFailure(RestException e) {
						logger.info("Failed recording bridge: " + ErrorStream.fromThrowable(e));
						future.completeExceptionally(e);
					}
				});

		return future;
	}

	/**
	 * get bridge details
	 * 
	 * @return
	 */
	public CompletableFuture<Bridge> getBridge() {
		CompletableFuture<Bridge> future = new CompletableFuture<Bridge>();
		arity.getAri().bridges().get(bridgeId, new AriCallback<Bridge>() {

			@Override
			public void onSuccess(Bridge result) {
				logger.info("Found bridge with id: " + bridgeId);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.info("Failed getting bridge: " + ErrorStream.fromThrowable(e));
				future.completeExceptionally(e);
			}
		});
		return future;
	}
}

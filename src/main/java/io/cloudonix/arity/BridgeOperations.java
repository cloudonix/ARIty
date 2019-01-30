package io.cloudonix.arity;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
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
	private String recordFormat = "ulaw";
	private int maxDurationSeconds;
	private int maxSilenceSeconds;
	private String ifExists;
	private boolean beep;
	private String terminateOn;
	private HashMap<String, RecordingData> recordings = new HashMap<>();
	private String bridgeType = "mixing";

	/**
	 * Constructor
	 * 
	 * @param arity instance of ARIty
	 */
	public BridgeOperations(ARIty arity) {
		this(arity, "", 0, 0, "overwrite", false, "#");
	}

	/**
	 * Constructor with more options
	 * 
	 * @param arity              instance of ARIty
	 * @param recordFormat       Format to encode audio in (to use the default
	 *                           'ulaw', use "")
	 * @param maxDurationSeconds Maximum duration of the recording, in seconds. 0
	 *                           for no limit
	 * @param maxSilenceSeconds  Maximum duration of silence, in seconds. 0 for no
	 *                           limit
	 * @param ifExists           Action to take if a recording with the same name
	 *                           already exists. Allowed values: fail, overwrite,
	 *                           append
	 * @param beep               true if need to play beep when recording begins,
	 *                           false otherwise
	 * @param terminateOn        DTMF input to terminate recording. Allowed values:
	 *                           none, any, *, #
	 */
	public BridgeOperations(ARIty arity, String recordFormat, int maxDurationSeconds, int maxSilenceSeconds,
			String ifExists, boolean beep, String terminateOn) {
		this.arity = arity;
		if (!Objects.equals(recordFormat, "") && Objects.nonNull(recordFormat))
			this.recordFormat = recordFormat;
		this.maxDurationSeconds = maxDurationSeconds;
		this.maxSilenceSeconds = maxSilenceSeconds;
		this.ifExists = ifExists;
		this.beep = beep;
		this.terminateOn = terminateOn;
		this.bridgeId = UUID.randomUUID().toString();
	}

	/**
	 * Create a new bridge
	 * 
	 * @param bridgeName name of the bridge to create
	 * 
	 * @return
	 */
	public CompletableFuture<Bridge> createBridge(String bridgeName) {
		logger.info("Creating bridge with name: " + bridgeName + ", with id: " + bridgeId + " , and bridge type: "
				+ bridgeType);
		return Operation.toFuture(cb -> arity.getAri().bridges().create(bridgeType, bridgeId, bridgeName, cb));
	}

	/**
	 * Shut down this bridge
	 * 
	 * @return
	 */
	public CompletableFuture<Void> destroyBridge() {
		logger.info("Destoying bridge with id: " + bridgeId);
		return Operation.<Void>toFuture(cb -> arity.getAri().bridges().destroy(bridgeId, cb)).thenAccept(v -> {
			recordings.clear();
			logger.info("Bridge was destroyed successfully. Bridge id: " + bridgeId);
		});
	}

	/**
	 * add new channel to this bridge
	 * 
	 * @param channelId id of the channel to add the bridge
	 * @return
	 */
	public CompletableFuture<Void> addChannelToBridge(String channelId) {
		logger.info("Adding channel with id: " + channelId + " to bridge with id: " + bridgeId);
		return Operation.<Void>toFuture(cb -> arity.getAri().bridges().addChannel(bridgeId, channelId, "member", cb));
	}

	/**
	 * remove channel from the bridge
	 * 
	 * @param channelId id of the channel to remove from the bridge
	 * @return
	 */
	public CompletableFuture<Void> removeChannelFromBridge(String channelId) {
		logger.info("Removing channel with id: " + channelId + " to bridge with id: " + bridgeId);
		return Operation.<Void>toFuture(cb -> arity.getAri().bridges().removeChannel(bridgeId, channelId, cb));
	}

	/**
	 * Play media to the bridge
	 * 
	 * @param fileToPlay name of the file to be played
	 * @return
	 */
	public CompletableFuture<Playback> playMediaToBridge(String fileToPlay) {
		logger.info("Play media to bridge with id: " + bridgeId + ", and media is: " + fileToPlay);
		String playbackId = UUID.randomUUID().toString();
		return Operation.<Playback>toFuture(
				cb -> arity.getAri().bridges().play(bridgeId, "sound:" + fileToPlay, "en", 0, 0, playbackId, cb))
				.thenCompose(result -> {
					CompletableFuture<Playback> future = new CompletableFuture<Playback>();
					logger.fine("playing: " + fileToPlay);
					arity.addFutureEvent(PlaybackFinished.class, bridgeId, (pbf, se) -> {
						if (!(pbf.getPlayback().getId().equals(playbackId)))
							return;
						logger.fine("PlaybackFinished id is the same as playback id.  ID is: " + playbackId);
						future.complete(pbf.getPlayback());
						se.unregister();
					});
					logger.fine("Future event of playbackFinished was added");
					return future;
				});
	}

	/**
	 * play music on hold to the bridge
	 * 
	 * @param musicOnHoldClass the class we want to load for playing music on hold
	 *                         to the bridge, defining needed configuration for
	 *                         playing (note: this class needs to be defined in
	 *                         musiconhold.conf file). If no need for specific
	 *                         configuration, use 'default'
	 * @return
	 */
	public CompletableFuture<Void> startMusicOnHold(String musicOnHoldClass) {
		CompletableFuture<Void> future = new CompletableFuture<Void>();
		arity.getAri().bridges().startMoh(bridgeId, musicOnHoldClass, new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.info("Playing music on hold to bridge: " + bridgeId);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.info("Failed playing music on hold to bridge: " + e);
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
				logger.info(" Stoped playing music on hold to bridge: " + bridgeId);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.info("Failed stop playing music on hold to bridge: " + e);
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	/**
	 * record the mixed audio from all channels participating in this bridge.
	 * 
	 * @param recordingName name of the recording
	 * @return
	 */
	public CompletableFuture<LiveRecording> recordBridge(String recordingName) {
		logger.info("Record bridge with id: " + bridgeId + ", and recording name is: " + recordingName);
		CompletableFuture<LiveRecording> future = new CompletableFuture<LiveRecording>();
		Instant recordingStartTime = Instant.now();
		arity.getAri().bridges().record(bridgeId, recordingName, recordFormat, maxDurationSeconds, maxSilenceSeconds,
				ifExists, beep, terminateOn, new AriCallback<LiveRecording>() {
					@Override
					public void onSuccess(LiveRecording result) {
						logger.info("Strated Recording bridge with id: " + bridgeId + " and recording name is: "
								+ recordingName);
						arity.addFutureEvent(RecordingFinished.class, bridgeId, (record, se) -> {
							if (!Objects.equals(record.getRecording().getName(), recordingName))
								return;
							long recordingEndTime = Instant.now().getEpochSecond();
							logger.info("Finished recording: " + recordingName);
							record.getRecording().setDuration(Integer.valueOf(
									String.valueOf(Math.abs(recordingEndTime - recordingStartTime.getEpochSecond()))));
							RecordingData recordingData = new RecordingData(recordingName, record.getRecording(),
									recordingStartTime);
							recordings.put(recordingName, recordingData);
							future.complete(record.getRecording());
							se.unregister();
						});
					}

					@Override
					public void onFailure(RestException e) {
						logger.info("Failed recording bridge: " + e);
						future.completeExceptionally(e);
					}
				});

		return future;
	}

	/**
	 * get bridge if exists
	 * 
	 * @return
	 */
	public CompletableFuture<Bridge> getBridge() {
		logger.info("Getting bridge with id: " + bridgeId + "...");
		CompletableFuture<Bridge> future = new CompletableFuture<Bridge>();
		arity.getAri().bridges().get(bridgeId, new AriCallback<Bridge>() {
			@Override
			public void onSuccess(Bridge result) {
				logger.info("Found bridge with id: " + bridgeId);
				future.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.info("Failed getting bridge: " + e);
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	/**
	 * get the id of the bridge
	 * 
	 * @return
	 */
	public String getBridgeId() {
		return bridgeId;
	}

	/**
	 * set the id of the bridge
	 * 
	 * @return
	 */
	public void setBridgeId(String bridgeId) {
		this.bridgeId = bridgeId;
	}

	/**
	 * get all recording for this bridge
	 * 
	 * @return
	 */
	public HashMap<String, RecordingData> getRecordings() {
		return recordings;
	}

	/**
	 * get a recording's data by it's name if exists
	 * 
	 * @param recordName name of the recording
	 * @return the recording's data if saved, null otherwise
	 */
	public RecordingData getRecodingByName(String recordName) {
		return recordings.get(recordName);
	}

	/**
	 * get a specified recording's data
	 * 
	 * @param recordingName name of the recording we are looking for
	 * @return
	 */
	public RecordingData getRecording(String recordingName) {
		for (Entry<String, RecordingData> currRecording : recordings.entrySet()) {
			if (Objects.equals(recordingName, currRecording.getKey()))
				return currRecording.getValue();
		}
		logger.info("No recording with name: " + recordingName);
		return null;
	}

	public void setBeep(boolean beep) {
		this.beep = beep;
	}

	/**
	 * get list of channels that are connected to the bridge
	 * 
	 * @return
	 */
	public CompletableFuture<List<String>> getChannelsInBridge() {
		logger.info("Getting all ids of active channels in bridge with id: " + bridgeId);
		return getBridge().thenApply(bridge -> {
			if (Objects.isNull(bridge)) {
				logger.warning("Bridge is null");
				return null;
			}
			return bridge.getChannels();
		});
	}

	/**
	 * get the type of the bridge
	 * 
	 * @return
	 */
	public String getBridgeType() {
		return bridgeType;
	}

	/**
	 * set the value of bridge. allowed values: mixing, dtmf_events, proxy_media,
	 * holding (the default is 'mixing')
	 * 
	 * @param bridgeType
	 * @return the update object
	 */
	public BridgeOperations setBridgeType(String bridgeType) {
		logger.info("Setting type of bridge with id: " + bridgeId + " to type:" + bridgeType);
		if (!Objects.equals(bridgeType, "mixing") && !Objects.equals(bridgeType, "dtmf_events")
				&& !Objects.equals(bridgeType, "proxy_media") && !Objects.equals(bridgeType, "holding")) {
			logger.warning("Invalid bridge type: " + bridgeType);
			return this;
		}
		this.bridgeType = bridgeType;
		return this;
	}

	/**
	 * get how many channels are connected to this bridge
	 * 
	 * @return number of active channels in this bridge
	 */
	public CompletableFuture<Integer> getNumberOfChannelsInBridge() {
		logger.info("Getting number of active channel in bridge with id: " + bridgeId);
		return getBridge().thenApply(bridgeRes -> bridgeRes.getChannels().size()).exceptionally(t -> {
			logger.severe("Failed getting bridge: " + t);
			return -1;
		});
	}
}

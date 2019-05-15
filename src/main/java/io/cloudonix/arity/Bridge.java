package io.cloudonix.arity;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ActionBridges;
import ch.loway.oss.ari4java.generated.ChannelEnteredBridge;
import ch.loway.oss.ari4java.generated.ChannelLeftBridge;
import ch.loway.oss.ari4java.generated.LiveRecording;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.RecordingFinished;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.bridge.BridgeNotFoundException;
import io.cloudonix.arity.errors.bridge.ChannelNotAllowedInBridge;
import io.cloudonix.arity.errors.bridge.ChannelNotInBridgeException;

/**
 * API for bridge operations
 * 
 * @author naamag
 * @author odeda
 */
public class Bridge {
	private ARIty arity;
	private final static Logger logger = Logger.getLogger(ARIty.class.getName());
	private String bridgeId;
	private HashMap<String, RecordingData> recordings = new HashMap<>();
	private String bridgeType = "mixing";
	private Runnable handlerChannelLeftBridge = () -> {
	};
	private Runnable handlerChannelEnteredBridge = () -> {
	};
	private ActionBridges api;
	private String name;

	/**
	 * Create a new Bridge object with a unique ID
	 * Use this constructor before creating a new bridge
	 * @param arity instance of ARIty
	 */
	public Bridge(ARIty arity) {
		this(arity, UUID.randomUUID().toString());
	}
	
	/**
	 * Create a new Bridge object to access a bridge with a known ID
	 * You should probably call {@link #reload()} after creating the object
	 * @param arity instance of ARIty
	 * @param id ID of bridge to access
	 */
	public Bridge(ARIty arity, String id) {
		this.arity = arity;
		this.bridgeId = id;
		this.api = arity.getAri().bridges();
	}

	/**
	 * Create a new bridge
	 * 
	 * @param bridgeName name of the bridge to create
	 * 
	 * @return
	 */
	public CompletableFuture<Bridge> create(String bridgeName) {
		logger.info("Creating bridge with name: " + bridgeName + ", with id: " + bridgeId + " , and bridge type: "
				+ bridgeType);
		return Operation.<ch.loway.oss.ari4java.generated.Bridge>retryOperation(cb -> api.create(bridgeType, bridgeId, bridgeName, cb))
				.thenApply(b -> {
					this.name = b.getName();
					return this;
				})
				.handle(this::mapExceptions);
	}

	/**
	 * Shut down this bridge
	 * 
	 * @return
	 */
	public CompletableFuture<Void> destroy() {
		logger.info("Destoying bridge with id: " + bridgeId);
		return Operation.<Void>retryOperation(cb -> api.destroy(bridgeId, cb)).thenAccept(v -> {
			recordings.clear();
			logger.info("Bridge was destroyed successfully. Bridge id: " + bridgeId);
		}).handle(this::mapExceptions);
	}

	/**
	 * add new channel to this bridge
	 * 
	 * @param channelId id of the channel to add the bridge
	 * @return
	 */
	public CompletableFuture<Void> addChannel(String channelId) {
		logger.info("Adding channel with id: " + channelId + " to bridge with id: " + bridgeId);
		arity.listenForOneTimeEvent(ChannelEnteredBridge.class, channelId, this::handleChannelEnteredBridge);
		return Operation
				.<Void>retryOperation(cb -> api.addChannel(bridgeId, channelId, "member", cb))
				.handle(this::mapExceptions);
	}

	/**
	 * handle when a channel entered the bridge
	 * 
	 * @param channelEnteredtBridge
	 */
	private void handleChannelEnteredBridge(ChannelEnteredBridge channelEnteredtBridge) {
		logger.fine("Channel with id: "+channelEnteredtBridge.getChannel().getId()+" entered the bridge");
		handlerChannelEnteredBridge.run();
	}

	/**
	 * remove channel from the bridge
	 * 
	 * @param channelId id of the channel to remove from the bridge
	 * @return
	 */
	public CompletableFuture<Void> removeChannel(String channelId) {
		logger.info("Removing channel with id: " + channelId + " to bridge with id: " + bridgeId);
		arity.listenForOneTimeEvent(ChannelLeftBridge.class, channelId, this::handleChannelLeftBridge);
		return Operation.<Void>retryOperation(cb -> api.removeChannel(bridgeId, channelId, cb))
				.handle(this::mapExceptions);
	}

	/**
	 * handle when a channel left the bridge
	 * 
	 * @param channelLeftBridge
	 */
	private void handleChannelLeftBridge(ChannelLeftBridge channelLeftBridge) {
		logger.fine("Channel with id: "+channelLeftBridge.getChannel().getId()+" left the bridge");
		handlerChannelLeftBridge.run();
	}

	/**
	 * Play media to the bridge
	 * 
	 * @param fileToPlay name of the file to be played
	 * @return
	 */
	public CompletableFuture<Playback> playMedia(String fileToPlay) {
		logger.info("Play media to bridge with id: " + bridgeId + ", and media is: " + fileToPlay);
		String playbackId = UUID.randomUUID().toString();
		return Operation.<Playback>retryOperation(
				cb -> api.play(bridgeId, "sound:" + fileToPlay, "en", 0, 0, playbackId, cb))
				.thenCompose(result -> {
					CompletableFuture<Playback> future = new CompletableFuture<Playback>();
					logger.fine("playing: " + fileToPlay);
					arity.addEventHandler(PlaybackFinished.class, bridgeId, (pbf, se) -> {
						if (!(pbf.getPlayback().getId().equals(playbackId)))
							return;
						logger.fine("PlaybackFinished id is the same as playback id.  ID is: " + playbackId);
						future.complete(pbf.getPlayback());
						se.unregister();
					});
					logger.fine("Future event of playbackFinished was added");
					return future;
				})
				.handle(this::mapExceptions);
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
		logger.fine("Try playing music on hold to bridge with id: " + bridgeId);
		return Operation.<Void>retryOperation(cb -> api.startMoh(bridgeId, musicOnHoldClass, cb))
				.handle(this::mapExceptions);
	}

	/**
	 * stop playing music on hold to the bridge
	 * 
	 * @return
	 */
	public CompletableFuture<Void> stopMusicOnHold() {
		logger.fine("Try to stop playing music on hold to bridge with id: " + bridgeId);
		return Operation.<Void>retryOperation(cb -> api.stopMoh(bridgeId, cb))
				.handle(this::mapExceptions);
	}

	/**
	 * record the mixed audio from all channels participating in this bridge.
	 * 
	 * @param recordingName name of the recording
	 * @return
	 */
	public CompletableFuture<LiveRecording> record(String recordingName) {
		return record(recordingName, "overwrite", false, "#", null, 0, 0);
	}
	
	/**
	 * record the mixed audio from all channels participating in this bridge.
	 * 
	 * @param recordingName name of the recording
	 * @param ifExists how to handle an existing recording with the same name. Possible values are:
	 *    "fail", "overwrite", "append"
	 * @param beep Whether to play a beep sound when the recording starts
	 * @param terminateOn DTMF signal to terminate recording on
	 * @param recordFormat Format for the recording. Set to null to use the default "ulaw"
	 * @param maxDurationSeconds Maximum length of the recording, in seconds. set to 0 for unlimited.
	 * @param maxSilenceSeconds Maximum seconds of silence for the recording to stop. set to 0 for none.
	 * @return
	 */
	public CompletableFuture<LiveRecording> record(String recordingName, String ifExists, boolean beep, String terminateOn, String recordFormat, int maxDurationSeconds, int maxSilenceSeconds) {
		recordFormat = Objects.isNull(recordFormat) ? "ulaw" : recordFormat;
		logger.info("Record bridge with id: " + bridgeId + ", and recording name is: " + recordingName);
		CompletableFuture<LiveRecording> future = new CompletableFuture<LiveRecording>();
		Instant recordingStartTime = Instant.now();
		api.record(bridgeId, recordingName, recordFormat, maxDurationSeconds, maxSilenceSeconds,
				ifExists, beep, terminateOn, new AriCallback<LiveRecording>() {
					@Override
					public void onSuccess(LiveRecording result) {
						logger.info("Strated Recording bridge with id: " + bridgeId + " and recording name is: "
								+ recordingName);
						arity.addEventHandler(RecordingFinished.class, bridgeId, (record, se) -> {
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
						logger.info("Failed recording bridge");
						future.completeExceptionally(e);
					}
				});

		return future;
	}

	private CompletableFuture<ch.loway.oss.ari4java.generated.Bridge> readBridge() {
		logger.info("Trying to get bridge with id: " + bridgeId + "...");
		return Operation.<ch.loway.oss.ari4java.generated.Bridge>retryOperation(cb -> api.get(bridgeId, cb))
				.handle(this::mapExceptions);
	}
	
	public CompletableFuture<Bridge> reload() {
		return readBridge().thenApply(b ->{
			name = b.getName();
			bridgeType = b.getBridge_type();
			return this;
		});
	}

	/**
	 * get the id of the bridge
	 * 
	 * @return
	 */
	public String getId() {
		return bridgeId;
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
	 * get the type of the bridge
	 * 
	 * @return
	 */
	public String getType() {
		return bridgeType;
	}
	
	/**
	 * Return the bridge name
	 * @return the bridge name if the bridge was ever created, <tt>null</tt> otherwise
	 */
	public String getName() {
		return name;
	}

	/**
	 * set the value of bridge. allowed values: mixing, dtmf_events, proxy_media,
	 * holding (the default is 'mixing')
	 * 
	 * @param bridgeType
	 * @return a reference to the Bridge object itself
	 */
	public Bridge setBridgeType(String bridgeType) {
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
	public CompletableFuture<Integer> getChannelCount() {
		return getChannels().thenApply(List::size);
	}

	/**
	 * Retrieve the channel IDs for all channels on the bridge
	 * @return list of channel IDs
	 */
	public CompletableFuture<List<String>> getChannels() {
		return readBridge().thenApply(bridgeRes -> bridgeRes.getChannels());
	}

	/**
	 * check if the this bridge is an active bridge in Asterisk
	 * 
	 * @return true if the bridge is active, false otherwise
	 */
	public CompletableFuture<Boolean> isActive() {
		return readBridge().thenApply(b -> {
			this.name = b.getName();
			return true;
		}).exceptionally(t -> false);
	}

	private <T> T mapExceptions(T val, Throwable error) {
		if (Objects.isNull(error))
			return val;
		while (error instanceof CompletionException)
			error = error.getCause();
		switch (error.getMessage()) {
		case "Bridge not found": throw new BridgeNotFoundException(error);
		case "Channel not found": throw new ChannelNotInBridgeException(error);
		case "Channel not in Stasis application": throw new ChannelNotAllowedInBridge(error.getMessage());
		}
		throw new CompletionException("Unexpected Bridge exception '" + error.getMessage() + "'", error);
	}
}
package io.cloudonix.arity;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.actions.ActionBridges;
import ch.loway.oss.ari4java.generated.models.ChannelEnteredBridge;
import ch.loway.oss.ari4java.generated.models.ChannelLeftBridge;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import ch.loway.oss.ari4java.generated.models.Playback;
import ch.loway.oss.ari4java.generated.models.PlaybackFinished;
import ch.loway.oss.ari4java.generated.models.RecordingFinished;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ARItyException;
import io.cloudonix.arity.errors.bridge.BridgeNotFoundException;
import io.cloudonix.arity.errors.bridge.ChannelNotAllowedInBridge;
import io.cloudonix.arity.errors.bridge.ChannelNotInBridgeException;
import io.cloudonix.arity.helpers.Futures;

/**
 * API for bridge operations
 *
 * @author naamag
 * @author odeda
 */
public class Bridge {

	private ARIty arity;
	private final static Logger logger = LoggerFactory.getLogger(Bridge.class);
	private String bridgeId;
	private HashMap<String, RecordingData> recordings = new HashMap<>();
	private String bridgeType = "mixing";
	private ActionBridges api;
	private String name;
	private ConcurrentHashMap<String, CompletableFuture<Void>> enteredEventListeners = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, CompletableFuture<Void>> leftEventListeners = new ConcurrentHashMap<>();

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
		return Operation.<ch.loway.oss.ari4java.generated.models.Bridge>retry(cb -> api.createWithId(bridgeId)
				.setType(bridgeType).setName(bridgeName).execute(cb),
				this::mapExceptions)
				.thenApply(b -> {
					this.name = b.getName();
					return this;
				});
	}

	/**
	 * Shut down this bridge
	 *
	 * @return
	 */
	public CompletableFuture<Void> destroy() {
		logger.info("Destroying bridge with id: " + bridgeId);
		return Operation.<Void>retry(cb -> api.destroy(bridgeId).execute(cb), this::mapExceptionsForDestroyBridge)
				.exceptionally(Futures.on(BridgeNotFoundException.class, e -> { return null; }))
				.thenAccept(v -> {
			recordings.clear();
			logger.info("Bridge was destroyed successfully. Bridge id: " + bridgeId);
		});
	}

	/**
	 * Add a channel to this bridge
	 *
	 * @param channelId id of the channel to add the bridge
	 * @return A promise for when ARI confirms the channel was added
	 */
	public CompletableFuture<Void> addChannel(String channelId) {
		return addChannel(channelId, false);
	}

	/**
	 * Add a channel to this bridge, optionally waiting for the ChannelEnteredBridge event
	 * to confirm channel was added.
	 *
	 * @param channelId id of the channel to add the bridge
	 * @param confirmWasAdded should we wait for the ChannelEnteredBride event
	 * @return A promise for when ARI confirms the channel was added or that it entererd the
	 *   bridge, as per <code>confirmWasAdded</code>
	 */
	public CompletableFuture<Void> addChannel(String channelId, boolean confirmWasAdded) {
		CompletableFuture<Void> waitForAdded = confirmWasAdded ?
				waitForChannelEntered(channelId) : CompletableFuture.completedFuture(null);
		logger.info("Adding channel with id: " + channelId + " to bridge with id: " + bridgeId);
		arity.listenForOneTimeEvent(ChannelEnteredBridge.class, channelId, this::handleChannelEnteredBridge);
		return Operation.<Void>retry(cb -> api.addChannel(bridgeId, channelId).setRole("member").execute(cb), this::mapExceptions)
				.thenCompose(v -> waitForAdded);
	}

	/**
	 * Get a {@link CompletableFuture} that will complete when the specified channel has
	 * entered the bridge.
	 *
	 * @param channelId ID of the channel to wait for to enter the bridge
	 * @return The promise that will be fulfilled when the channel enters the bridge
	 */
	private CompletableFuture<Void> waitForChannelEntered(String channelId) {
		return enteredEventListeners.computeIfAbsent(channelId, i -> new CompletableFuture<>());
	}

	/**
	 * Get a {@link CompletableFuture} that will complete when the specified channel has
	 * left the bridge.
	 *
	 * @param channelId ID of the channel to wait for to enter the bridge
	 * @return The promise that will be fulfilled when the channel leaves the bridge
	 */
	public CompletableFuture<Void> waitForChannelLeft(String channelId) {
		return leftEventListeners.computeIfAbsent(channelId, i -> new CompletableFuture<>());
	}

	/**
	 * Trigger "channel entered bridge" event listeners
	 * @param channelEnteredtBridge event for the channel
	 */
	private void handleChannelEnteredBridge(ChannelEnteredBridge channelEnteredtBridge) {
		String chanId = channelEnteredtBridge.getChannel().getId();
		logger.debug("Channel with id: "+chanId+" entered the bridge");
		CompletableFuture<Void> event = enteredEventListeners.remove(chanId);
		if (Objects.nonNull(event))
			event.complete(null);
	}

	/**
	 * Removes a channel that is in the bridge
	 * @param channelId id of the channel to remove from the bridge
	 * @return A promise for when ARI confirms the channel was added
	 */
	public CompletableFuture<Void> removeChannel(String channelId) {
		return removeChannel(channelId, false);
	}

	/**
	 * Removes a channel that is in the bridge
	 * @param channelId id of the channel to remove from the bridge
	 * @param confirmWasRemoved should we wait for the ChannelLeftBridge event
	 * @return A promise for when ARI confirms the channel was added or that it entererd the
	 *   bridge, as per <code>confirmWasRemoved</code>
	 */
	public CompletableFuture<Void> removeChannel(String channelId, boolean confirmWasRemoved) {
		CompletableFuture<Void> waitForRemoved = confirmWasRemoved ?
				waitForChannelLeft(channelId) : CompletableFuture.completedFuture(null);
		logger.info("Removing channel with id: " + channelId + " to bridge with id: " + bridgeId);
		arity.listenForOneTimeEvent(ChannelLeftBridge.class, channelId, this::handleChannelLeftBridge);
		return Operation.<Void>retry(cb -> api.removeChannel(bridgeId, channelId).execute(cb), this::mapExceptions)
				.exceptionally(Futures.on(ChannelNotInBridgeException.class, e -> null))
				.thenCompose(v -> waitForRemoved);
	}

	/**
	 * Trigger "channel left bridge" event listeners
	 * @param channelLeftBridge event for the channel
	 */
	private void handleChannelLeftBridge(ChannelLeftBridge channelLeftBridge) {
		String chanId = channelLeftBridge.getChannel().getId();
		logger.debug("Channel with id: "+chanId+" left the bridge");
		CompletableFuture<Void> event = leftEventListeners.remove(chanId);
		if (Objects.nonNull(event))
			event.complete(null);
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
		return Operation.<Playback>retry(
				cb -> api.play(bridgeId, "sound:" + fileToPlay).setLang("en").setPlaybackId(playbackId).execute(cb), this::mapExceptions)
				.thenCompose(result -> {
					CompletableFuture<Playback> future = new CompletableFuture<Playback>();
					logger.debug("playing: " + fileToPlay);
					arity.addEventHandler(PlaybackFinished.class, bridgeId, (pbf, se) -> {
						if (!(pbf.getPlayback().getId().equals(playbackId)))
							return;
						logger.debug("PlaybackFinished id is the same as playback id.  ID is: " + playbackId);
						future.complete(pbf.getPlayback());
						se.unregister();
					});
					logger.debug("Future event of playbackFinished was added");
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
		logger.debug("Try playing music on hold to bridge with id: " + bridgeId);
		return Operation.<Void>retry(cb -> api.startMoh(bridgeId).setMohClass(musicOnHoldClass).execute(cb), this::mapExceptions);
	}

	/**
	 * stop playing music on hold to the bridge
	 *
	 * @return
	 */
	public CompletableFuture<Void> stopMusicOnHold() {
		logger.debug("Try to stop playing music on hold to bridge with id: " + bridgeId);
		return Operation.<Void>retry(cb -> api.stopMoh(bridgeId).execute(cb), this::mapExceptions);
	}

	/**
	 * record the mixed audio from all channels participating in this bridge.
	 *
	 * @param recordingName name of the recording
	 * @return
	 */
	public CompletableFuture<RecordingData> record(String recordingName) {
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
	public CompletableFuture<RecordingData> record(String recordingName, String ifExists, boolean beep, String terminateOn, String recordFormat, int maxDurationSeconds, int maxSilenceSeconds) {
		String realRecordFormat = Objects.isNull(recordFormat) ? "ulaw" : recordFormat;
		logger.info("Record bridge with id: " + bridgeId + ", and recording name is: " + recordingName);
		RecordingData recordingData = new RecordingData(arity, recordingName);
		recordings.put(recordingName, recordingData);
		
		arity.addEventHandler(RecordingFinished.class, bridgeId, (record, se) -> {
			if (!Objects.equals(record.getRecording().getName(), recordingName)) {
				logger.warn("Unexpected recording finished for bridge id " + bridgeId + " with name: " + 
						record.getRecording().getName());
				return;
			}
			logger.info("Finished recording: " + recordingName);
			recordingData.setLiveRecording(record.getRecording());
			se.unregister();
		});
		
		return Operation.<LiveRecording>retry(cb -> api.record(bridgeId, recordingName, realRecordFormat)
				.setMaxDurationSeconds(maxDurationSeconds).setMaxSilenceSeconds(maxSilenceSeconds)
				.setIfExists(ifExists).setBeep(beep).setTerminateOn(terminateOn).execute(cb))
				.thenApply(result -> {
					logger.info("Started Recording bridge with id: " + bridgeId + " and recording name is: "
							+ recordingName);
					recordingData.setLiveRecording(result);
					return recordingData;
				})
				.exceptionally(Futures.on(RestException.class, e -> {
					logger.error("Failed to record bridge " + this, e);
					throw e;
				}));
	}
	
	public CompletableFuture<RecordingData> stopRecording(String recordingName) {
		RecordingData data = getRecodingByName(recordingName);
		if (Objects.isNull(data))
			return CompletableFuture.completedFuture(data);
		return Operation.<Void>retry(cb -> arity.getAri().recordings().stop(recordingName).execute(cb))
				.thenApply(v -> data);
	}

	private CompletableFuture<ch.loway.oss.ari4java.generated.models.Bridge> readBridge() {
		logger.info("Trying to get bridge with id: " + bridgeId + "...");
		return Operation.<ch.loway.oss.ari4java.generated.models.Bridge>retry(cb -> api.get(bridgeId).execute(cb), this::mapExceptions);
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
			logger.warn("Invalid bridge type: " + bridgeType);
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

	private Exception mapExceptions(Throwable ariError) {
		switch (ariError.getMessage()) {
		case "Bridge not found": return new BridgeNotFoundException(bridgeId, ariError);
		case "Channel not in Stasis application": return new ChannelNotAllowedInBridge(bridgeId, ariError.getMessage());
		case "Channel not in this bridge": return new ChannelNotInBridgeException(bridgeId, ariError);
		}
		return ARItyException.ariRestExceptionMapper(ariError);
	}
	
	private Exception mapExceptionsForDestroyBridge(Throwable ariError) {
		switch (ariError.getMessage()) {
		case "Bridge not found": return new BridgeNotFoundException(bridgeId, ariError);
		}
		return ARItyException.ariRestExceptionMapper(ariError);
	}
	
	@Override
	public String toString() {
		return "Bridge " + (name != null ? name + " ": "") + bridgeId; 
	}
}

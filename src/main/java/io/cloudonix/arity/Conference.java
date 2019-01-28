package io.cloudonix.arity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.LiveRecording;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.ChannelHangupRequest_impl_ari_2_0_0;
import io.cloudonix.future.helper.FutureHelper;

/**
 * The class handles and saves all needed information for a conference call
 * 
 * @author naamag
 *
 */
public class Conference {
	private String confName;
	// channel id's of all channels in the conference
	private List<String> channelIdsInConf = new CopyOnWriteArrayList<>();
	private CallController callController;
	private final static Logger logger = Logger.getLogger(Conference.class.getName());
	private String bridgeId = null;
	private Runnable runHangup = () -> {
	};
	private boolean beep = false;
	private boolean mute = false;
	private boolean needToRecord;
	private String recordName = "";
	private LiveRecording conferenceRecord;
	private BridgeOperations bridgeOperations;
	private String musicOnHoldClassName = "default";
	private ARIty arity;

	/**
	 * Constructor
	 * 
	 * @param callController call Controller instance
	 * @param name           name of the conference
	 */
	public Conference(CallController callController, String name) {
		this(callController, name, false, false, false, "default");
	}

	/**
	 * Constructor with more functionality
	 * 
	 */
	public Conference(CallController callController, String name, boolean beep, boolean mute, boolean needToRecord,
			String musicOnHoldClassName) {
		this.arity = callController.getARItyService();
		this.callController = callController;
		this.confName = name;
		this.beep = beep;
		this.mute = mute;
		this.needToRecord = needToRecord;
		this.musicOnHoldClassName = musicOnHoldClassName;
		this.bridgeOperations = new BridgeOperations(arity);
		this.bridgeOperations.setBeep(beep);
	}

	/**
	 * close conference
	 * 
	 * @return
	 */
	public CompletableFuture<Void> closeConference() {
		logger.info("Closing conference");
		return bridgeOperations.destroyBridge();
	}

	/**
	 * add channel to the conference
	 * 
	 * @param newChannelId id of the new channel that we want to add to add to the
	 *                     conference
	 */
	public CompletableFuture<Conference> addChannelToConf(String newChannelId) {
		CompletableFuture<Conference> confFuture = new CompletableFuture<Conference>();
		if (Objects.isNull(bridgeId))
			bridgeId = confName;
		return callController.answer().run().thenCompose(answerRes -> bridgeOperations.addChannelToBridge(newChannelId))
				.thenCompose(v -> {
					logger.fine("Channel was added to the bridge");
					return beep ? bridgeOperations.playMediaToBridge("beep") : FutureHelper.completedSuccessfully(null);
				}).thenCompose(beepRes -> {
					channelIdsInConf.add(newChannelId);
					arity.addFutureOneTimeEvent(ChannelHangupRequest_impl_ari_2_0_0.class, newChannelId,
							this::removeAndCloseIfEmpty);
					return mute ? callController.mute(newChannelId, "out").run()
							: FutureHelper.completedSuccessfully(null);
				}).thenCompose(muteRes -> annouceUser("joined")).thenCompose(pb -> {
					if (channelIdsInConf.size() == 1) {
						return bridgeOperations.playMediaToBridge("conf-onlyperson").thenCompose(playRes -> {
							logger.info("1 person in the conference");
							return bridgeOperations.startMusicOnHold(musicOnHoldClassName)
									.thenAccept(v2 -> logger.info("Playing music to bridge with id " + bridgeId));
						});
					} else {
						// at least 2 channels are in the conference
						logger.info(
								channelIdsInConf.size() + " are at conefernce " + confName + " , conference started");
						return bridgeOperations.stopMusicOnHold().thenCompose(v3 -> {
							logger.info("Stoped playing music on hold to the conference bridge");
							if (needToRecord) {
								logger.info("Start recording conference " + confName);
								if (Objects.equals(recordName, ""))
									recordName = UUID.randomUUID().toString();
								return bridgeOperations.recordBridge(recordName).thenAccept(recored -> {
									conferenceRecord = recored;
									logger.info("Done recording");
								});
							}
							logger.fine("Not recording conference");
							return CompletableFuture.completedFuture(null);
						});
					}
				}).exceptionally(t -> {
					logger.info("Unable to add channel to conference: " + t);
					confFuture.completeExceptionally(t);
					return null;
				}).thenCompose(v -> {
					confFuture.complete(this);
					return confFuture;
				});
	}

	/**
	 * register hang up event handler
	 * 
	 * @param func runnable function that will be running when hang up occurs
	 * @return
	 */
	public Conference whenHangUp(Runnable func) {
		runHangup = func;
		return this;
	}

	/**
	 * when hang up occurs, remove channel from conference and close conference if
	 * empty
	 * 
	 * @param hangup hang up channel event instance
	 * @return
	 */
	private void removeAndCloseIfEmpty(ChannelHangupRequest_impl_ari_2_0_0 hangup) {
		runHangup.run();
		logger.info("Channel " + hangup.getChannel().getId() + " left conference: " + confName);
		channelIdsInConf.remove(hangup.getChannel().getId());
		if (channelIdsInConf.isEmpty())
			closeConference()
					.thenAccept(v2 -> logger.info("Nobody in the conference, closed the conference" + confName))
					.exceptionally(t -> {
						logger.warning("Conference bridge was already destroyed");
						return null;
					});
	}

	/**
	 * Announce new channel joined/left a conference
	 * 
	 * @param status 'joined' or 'left' conference
	 */
	private CompletableFuture<Playback> annouceUser(String status) {
		return (Objects.equals(status, "joined")) ? bridgeOperations.playMediaToBridge("confbridge-has-joined")
				: bridgeOperations.playMediaToBridge("conf-hasleft");
	}

	/**
	 * get conference name
	 * 
	 * @return
	 */
	public String getConfName() {
		return confName;
	}

	/**
	 * set conference name
	 * 
	 * @return
	 */
	public void setConfName(String confName) {
		this.confName = confName;
	}

	/**
	 * get number of channels in conference
	 * 
	 * @return
	 */
	public int getCount() {
		return channelIdsInConf.size();
	}

	/**
	 * get list of channels connected to the conference bridge
	 * 
	 * @return
	 */
	public List<String> getChannelsInConf() {
		return channelIdsInConf;
	}

	/**
	 * set list of channels connected to the conference bridge
	 * 
	 * @return
	 */
	public void setChannelsInConf(List<String> channelsInConf) {
		this.channelIdsInConf = channelsInConf;
	}

	/**
	 * get recording name of the conference
	 * 
	 * @return
	 */
	public String getRecordName() {
		return recordName;
	}

	/**
	 * set recording name of the conference
	 * 
	 * @return
	 */
	public void setRecordName(String recordName) {
		this.recordName = recordName;
	}

	/**
	 * get the recording of the conference
	 * 
	 * @return
	 */
	public LiveRecording getConferenceRecord() {
		return conferenceRecord;
	}

	/**
	 * get music on hold file name of the conference
	 * 
	 * @return
	 */
	public String getMusicOnHoldFileName() {
		return musicOnHoldClassName;
	}

	/**
	 * set music on hold class for conference, defining the needed configuration for
	 * playing music on hold
	 * 
	 * @return
	 */
	public void setMusicOnHoldClassName(String musicOnHoldClassName) {
		this.musicOnHoldClassName = musicOnHoldClassName;
	}

	/**
	 * get recording start time
	 * 
	 * @return
	 */
	public String getRecordingStartTime() {
		RecordingData recordData = bridgeOperations.getRecodingByName(recordName);
		return Objects.nonNull(recordData) ? recordData.getStartingTime() : null;
	}

	/**
	 * check if there is a bridge for the conference
	 * 
	 * @return true if there is a bridge, false otherwise
	 */
	public CompletableFuture<Boolean> isConfereBridgeExists() {
		return bridgeOperations.getBridge().thenApply(bridgeRes -> true).exceptionally(t -> false);
	}

	/**
	 * create a bridge for the conference
	 * 
	 * @return the conference bridge
	 */
	public CompletableFuture<Bridge> createConferenceBridge() {
		return bridgeOperations.createBridge(confName).thenApply(bridgeRes -> {
			logger.info("Created a conference bridge");
			return bridgeRes;
		});
	}

	/**
	 * set the bridge id of the conference bridge
	 * 
	 * @param bridgeId new bridge id for the conference bridge
	 */
	public void setBridgeId(String bridgeId) {
		logger.fine("Changing bridge id to: " + bridgeId);
		bridgeOperations.setBridgeId(bridgeId);
	}

	/**
	 * remove a channel from this conference
	 * 
	 * @param channelId the id of the channel we want to remove
	 * @return
	 */
	public CompletableFuture<Void> removeChannelFromConf(String channelId) {
		if (!channelIdsInConf.contains(channelId)) {
			logger.info("Channel with id: " + channelId + " is not in conference " + confName);
			return FutureHelper.completedFuture();
		}
		return bridgeOperations.removeChannelFromBridge(channelId);
	}
}

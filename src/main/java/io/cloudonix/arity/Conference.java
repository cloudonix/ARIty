package io.cloudonix.arity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.ChannelLeftBridge;
import ch.loway.oss.ari4java.generated.LiveRecording;
import ch.loway.oss.ari4java.generated.Playback;
import io.cloudonix.arity.errors.ConferenceException;
import io.cloudonix.future.helper.FutureHelper;

/**
 * The class handles and saves all needed information for a conference call
 * 
 * @author naamag
 *
 */
public class Conference {
	private String confName;
	private CallController callController;
	private final static Logger logger = Logger.getLogger(Conference.class.getName());
	private String bridgeId = null;
	private Runnable handleChannelLeftConference = () -> {};
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
		return callController.answer().run()
				.thenCompose(answerRes -> bridgeOperations.addChannelToBridge(newChannelId))
				.thenCompose(v -> {
					logger.fine("Channel was added to the bridge");
					return beep ? bridgeOperations.playMediaToBridge("beep") : FutureHelper.completedSuccessfully(null);
				}).thenCompose(beepRes -> {
					arity.addFutureOneTimeEvent(ChannelLeftBridge.class, newChannelId, this::channelLeftConference);
					return mute ? callController.mute(newChannelId, "out").run()
							: FutureHelper.completedSuccessfully(null);
				}).thenCompose(muteRes -> {
					return annouceUser("joined").thenCompose(pb -> bridgeOperations.getNumberOfChannelsInBridge());
				}).thenCompose(numOfChannelsInConf -> {
					if (numOfChannelsInConf == -1)
						return FutureHelper.completedExceptionally(
								new ConferenceException("Failed getting size of conference bridge"));
					if (numOfChannelsInConf == 1) {
						return bridgeOperations.playMediaToBridge("conf-onlyperson").thenCompose(playRes -> {
							logger.info("1 person in the conference");
							return bridgeOperations.startMusicOnHold(musicOnHoldClassName)
									.thenAccept(v2 -> logger.info("Playing music to bridge with id " + bridgeId));
						});
					} else {
						// at least 2 channels are in the conference
						logger.info(numOfChannelsInConf + " are at conefernce " + confName + " , conference started");
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
					confFuture.completeExceptionally(new ConferenceException(t));
					return null;
				}).thenCompose(v -> {
					if (Objects.nonNull(confFuture))
						confFuture.complete(this);
					return confFuture;
				});
	}

	/**
	 * register handler to execute when channel left conference
	 * 
	 * @param handler runnable function that will be running when channel left bridge occurs
	 * @return
	 */
	public Conference registerChannelLeftConferenceHandler(Runnable handler) {
		handleChannelLeftConference = handler;
		return this;
	}

	/**
	 * handle when a channel left conference bridge
	 * 
	 * @param channelLeftBridge channelLeftBridge event instance
	 */
	private void channelLeftConference(ChannelLeftBridge channelLeftBridge) {
		handleChannelLeftConference.run();
		logger.info("Channel " + channelLeftBridge.getChannel().getId() + " left conference: " + confName);
		annouceUser("left").thenAccept(pb -> {
			bridgeOperations.getNumberOfChannelsInBridge().thenAccept(numberOfChannelsInConf -> {
				if (numberOfChannelsInConf == 0) {
					closeConference()
							.thenAccept(v2 -> logger.info("Nobody in the conference, closed the conference" + confName))
							.exceptionally(t -> {
								logger.warning("Conference bridge was already destroyed");
								return null;
							});
				}

			});
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
	public CompletableFuture<Integer> getCount() {
		return bridgeOperations.getNumberOfChannelsInBridge();
	}

	/**
	 * get list of channels connected to the conference bridge
	 * 
	 * @return
	 */
	public CompletableFuture<List<String>> getChannelsInConf() {
		return bridgeOperations.getChannelsInBridge();
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
		return bridgeOperations.removeChannelFromBridge(channelId);
	}
}

package io.cloudonix.arity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.LiveRecording;
import ch.loway.oss.ari4java.generated.Playback;
import io.cloudonix.future.helper.FutureHelper;

/**
 * The class handles and saves all needed information for a conference call
 * 
 * @author naamag
 *
 */
public class Conference extends Operation {

	private CompletableFuture<Conference> compFuture = new CompletableFuture<>(); // Not sure if this future is needed..
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
	private String musicOnHoldFileName;

	/**
	 * Constructor
	 * 
	 * @param callController call Controller instance
	 * @param name           name of the conference
	 */
	public Conference(CallController callController, String name) {
		this(callController, name, false, false, false, "");
	}

	/**
	 * Constructor with more functionality
	 * 
	 */
	public Conference(CallController callController, String name, boolean beep, boolean mute, boolean needToRecord,
			String musicOnHoldFileName) {
		super(callController.getCallState().getChannelID(), callController.getCallState().getArity(),
				callController.getCallState().getAri());
		this.callController = callController;
		this.confName = name;
		this.beep = beep;
		this.mute = mute;
		this.needToRecord = needToRecord;
		this.musicOnHoldFileName = musicOnHoldFileName;
		this.bridgeOperations = new BridgeOperations(getArity());
		this.bridgeOperations.setBeep(beep);
	}

	@Override
	public CompletableFuture<Conference> run() {
		if (Objects.isNull(bridgeId))
			getConferneceBridge().thenAccept(bridgeRes -> {
				if (Objects.isNull(bridgeRes)) {
					logger.info("Creating bridge for conference");
					bridgeOperations.createBridge().thenAccept(bridge -> bridgeId = bridge.getId());
				} else
					bridgeId = bridgeRes.getId();
			});
		return compFuture;
	}

	/**
	 * get conference bridge if exist
	 * 
	 * @return
	 */
	private CompletableFuture<Bridge> getConferneceBridge() {
		return bridgeOperations.getBridge().exceptionally(t -> null);
	}

	/**
	 * close conference when no users are connected to it
	 * 
	 * @return
	 */
	private CompletableFuture<Void> closeConference() {
		logger.info("Closing conference");
		compFuture.complete(this);
		return bridgeOperations.destroyBridge();
	}

	/**
	 * add channel to the conference
	 * 
	 * @param newChannelId id of the new channel that we want to add to add to the
	 *                     conference
	 */
	public CompletableFuture<Conference> addChannelToConf(String newChannelId) {
		if (Objects.isNull(bridgeId))
			bridgeId = confName;
		bridgeOperations.setBridgeId(bridgeId);
		return callController.answer().run()
				.thenCompose(answerRes -> bridgeOperations.addChannelToBridge(newChannelId))
				.thenCompose(v -> {
					logger.fine("Channel was added to the bridge");
					return beep ? bridgeOperations.playMediaToBridge("beep") : FutureHelper.completedSuccessfully(null);
				}).thenCompose(beepRes -> {
					channelIdsInConf.add(newChannelId);
					getArity().addFutureOneTimeEvent(ChannelHangupRequest.class, newChannelId, this::removeAndCloseIfEmpty);
					return mute ? callController.mute(newChannelId, "out").run()
							: FutureHelper.completedSuccessfully(null);
				}).thenCompose(muteRes -> annouceUser("joined"))
				.thenCompose(pb -> {
					if (channelIdsInConf.size() == 1) {
						bridgeOperations.playMediaToBridge("conf-onlyperson")
						.thenCompose(playRes -> {
							logger.info("1 person in the conference");
							return bridgeOperations.startMusicOnHold(musicOnHoldFileName).thenCompose(v2 -> {
								logger.info("Playing music to bridge with id " + bridgeId);
								return compFuture;
							});
						});
					}
					if (channelIdsInConf.size() == 2) {
						logger.info("2 channels are at conefernce " + confName + " , conference started");
						return bridgeOperations.stopMusicOnHold().thenCompose(v3 -> {
							logger.info("Stoped playing music on hold to the conference bridge");
							if (needToRecord) {
								logger.info("Start recording conference " + confName);
								if (Objects.equals(recordName, ""))
									recordName = UUID.randomUUID().toString();
								bridgeOperations.recordBridge(recordName).thenAccept(recored -> {
									conferenceRecord = recored;
									logger.info("Done recording");
								});
							}
							compFuture.complete(this);
							return compFuture;
						});
					}
					logger.fine("There are " + channelIdsInConf.size() + " channels in conference " + confName);
					return compFuture;
				}).exceptionally(t -> {
					logger.info("Unable to add channel to conference " + t);
					return null;
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
	private void removeAndCloseIfEmpty(ChannelHangupRequest hangup) {
		runHangup.run();
		bridgeOperations.removeChannelFromBridge(hangup.getChannel().getId()).thenAccept(v1 -> {
			logger.info("Channel "+ hangup.getChannel().getId()+" was removed conference " + confName);
			channelIdsInConf.remove(hangup.getChannel().getId());
			if (channelIdsInConf.isEmpty())
				closeConference()
						.thenAccept(v2 -> logger.info("Nobody in the conference, closed the conference" + confName));
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
	 * @return
	 */
	public String getRecordName() {
		return recordName;
	}

	/**
	 * set recording name of the conference
	 * @return
	 */
	public void setRecordName(String recordName) {
		this.recordName = recordName;
	}

	/**
	 * get the recording of the conference
	 * @return
	 */
	public LiveRecording getConferenceRecord() {
		return conferenceRecord;
	}
	
	/**
	 * get music on hold file name of the conference
	 * @return
	 */
	public String getMusicOnHoldFileName() {
		return musicOnHoldFileName;
	}

	/**
	 * set music on hold file name of the conference
	 * @return
	 */
	public void setMusicOnHoldFileName(String musicOnHoldFileName) {
		this.musicOnHoldFileName = musicOnHoldFileName;
	}
}

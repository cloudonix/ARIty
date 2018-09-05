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

/**
 * The class handles and saves all needed information for a conference call
 * 
 * @author naamag
 *
 */
public class Conference extends Operation {

	private CompletableFuture<Conference> compFuture = new CompletableFuture<>();
	private String confName;
	// channel id's of all channels in the conference
	private List<String> channelIdsInConf = new CopyOnWriteArrayList<>();
	private CallController callController;
	private final static Logger logger = Logger.getLogger(Conference.class.getName());
	private String bridgeId = null;
	private Runnable runHangup = null;
	private boolean beep;
	private boolean mute;
	private boolean needToRecord;
	private String recordName = "";
	private LiveRecording conferenceRecord;
	private BridgeOperations bridgeOperations;

	/**
	 * Constructor
	 * 
	 * @param callController
	 *            call Controller instance
	 * @param name
	 *            name of the conference
	 */
	public Conference(CallController callController, String name) {
		this(callController,name,false,false,false);
	}

	/**
	 * Constructor with more functionality
	 * 
	 */
	public Conference(CallController callController, String name, boolean beep, boolean mute, boolean needToRecord) {
		super(callController.getCallState().getChannelID(), callController.getCallState().getArity(),
				callController.getCallState().getAri());
		this.callController = callController;
		this.confName = name;
		this.beep = beep;
		this.mute = mute;
		this.needToRecord = needToRecord;
		this.bridgeOperations = new BridgeOperations(getArity());
		this.bridgeOperations.setBeep(beep);
	}

	@Override
	public CompletableFuture<Conference> run() {
		if (Objects.isNull(bridgeId))
			createOrConnectConference().thenAccept(bridgeRes -> bridgeId = bridgeRes.getId());
		return compFuture;
	}

	/**
	 * search conference bridge or create a new one if it does not exist
	 * 
	 * @return
	 */
	private CompletableFuture<Bridge> createOrConnectConference() {
		bridgeOperations.setBridgeId(bridgeId);
		return bridgeOperations.getBridge();
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
	 * @param newChannelId
	 *            id of the new channel that we want to add to add to the conference
	 */
	public CompletableFuture<Conference> addChannelToConf(String newChannelId) {
		if (Objects.isNull(bridgeId))
			bridgeId = confName;
		bridgeOperations.setBridgeId(bridgeId);
		return callController.answer().run().thenCompose(answerRes -> bridgeOperations.addChannelToBridge(newChannelId))
				.thenCompose(v -> {
					logger.fine("Channel was added to the bridge");
					if (!beep)
						return CompletableFuture.completedFuture(null);
					else
						return bridgeOperations.playMediaToBridge("beep");
				}).thenCompose(beepRes -> {
					channelIdsInConf.add(newChannelId);
					getArity().addFutureEvent(ChannelHangupRequest.class, newChannelId, this::removeAndCloseIfEmpty,
							true);
					if (!mute)
						return CompletableFuture.completedFuture(null);
					else
						return callController.mute(newChannelId, "out").run();
				}).thenCompose(muteRes -> annouceUser("joined")).thenCompose(pb -> {
					if (channelIdsInConf.size() == 1) {
						bridgeOperations.playMediaToBridge("conf-onlyperson").thenCompose(playRes -> {
							logger.info("1 person in the conference");
							return  bridgeOperations.startMusicOnHold("").thenCompose(v2 -> {
								logger.info("Playing music to bridge with id " + bridgeId);
								compFuture.complete(this);
								return compFuture;
							});
						});
					}
					if (channelIdsInConf.size() == 2) {
						logger.info("2 channels are at conefernce " + confName + " , conference started");
						return bridgeOperations.stopMusicOnHold().thenCompose(v3 -> {
							if (needToRecord) {
								logger.info("Start recording conference " + confName);
								if (Objects.equals(recordName, ""))
									recordName = UUID.randomUUID().toString();
								bridgeOperations.recordBridge(recordName).thenAccept(recored->{
									conferenceRecord = recored;
									logger.info("Done recording");
								});
							}
							logger.info("stoped playing music on hold to the conference bridge");
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
	 * @param func
	 *            runnable function that will be running when hang up occurs
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
	 * @param hangup
	 *            hang up channel event instance
	 * @return
	 */
	private boolean removeAndCloseIfEmpty(ChannelHangupRequest hangup) {
		if (!channelIdsInConf.contains(hangup.getChannel().getId())) {
			logger.info(
					"channel with id " + hangup.getChannel().getId() + " is not connected to conference " + confName);
			return false;
		}
		if (Objects.nonNull(runHangup))
			runHangup.run();
		else {
			bridgeOperations.removeChannelFromBridge(hangup.getChannel().getId()).thenAccept(v1 -> {
				logger.info("Channel left conference " + confName);
				if (channelIdsInConf.isEmpty())
					closeConference().thenAccept(
							v2 -> logger.info("Nobody in the conference, closed the conference" + confName));
			});
		}
		logger.fine("Caller hang up, stop recording conference");
		return true;
	}

	/**
	 * Announce new channel joined/left a conference
	 * 
	 * @param status
	 *            'joined' or 'left' conference
	 */
	private CompletableFuture<Playback> annouceUser(String status) {
		if (Objects.equals(status, "joined"))
			return bridgeOperations.playMediaToBridge("confbridge-has-joined");
		else
			return bridgeOperations.playMediaToBridge("conf-hasleft");
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

	public String getRecordName() {
		return recordName;
	}

	public void setRecordName(String recordName) {
		this.recordName = recordName;
	}

	public LiveRecording getConferenceRecord() {
		return conferenceRecord;
	}
}

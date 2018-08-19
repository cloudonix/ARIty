package io.cloudonix.arity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ErrorStream;
import io.cloudonix.future.helper.FutureHelper;

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
	private boolean beep = false;
	private boolean mute = false;
	private boolean needToRecord;
	private String recordName = "";
	private Record conferenceRecord;

	/**
	 * Constructor
	 * 
	 * @param callController
	 *            call Controller instance
	 * @param name
	 *            name of the conference
	 */
	public Conference(CallController callController, String name) {
		super(callController.getCallState().getChannelID(), callController.getCallState().getArity(),
				callController.getCallState().getAri());
		this.callController = callController;
		this.confName = name;
	}

	/**
	 * Constructor with more functionality
	 */
	public Conference(CallController callController, String name, boolean beep, boolean mute, boolean needToRecord) {
		super(callController.getCallState().getChannelID(), callController.getCallState().getArity(),
				callController.getCallState().getAri());
		this.callController = callController;
		this.confName = name;
		this.beep = beep;
		this.mute = mute;
		this.needToRecord = needToRecord;
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
		CompletableFuture<Bridge> bridgeFuture = new CompletableFuture<Bridge>();

		getAri().bridges().get(bridgeId, new AriCallback<Bridge>() {

			@Override
			public void onSuccess(Bridge result) {
				logger.info("Conference bridge was found");
				bridgeFuture.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.info("Creating conference " + confName);
				getAri().bridges().create("mixing", bridgeId, confName, new AriCallback<Bridge>() {
					@Override
					public void onSuccess(Bridge result) {
						bridgeId = result.getId();
						logger.info("Conference: " + confName + " is ready to use");
						bridgeFuture.complete(result);
					}

					@Override
					public void onFailure(RestException e) {
						logger.warning("Failed creating bridge for conference " + confName + " "
								+ ErrorStream.fromThrowable(e));
						bridgeFuture.completeExceptionally(e);
					}
				});
			}
		});
		return bridgeFuture;
	}

	/**
	 * close conference when no users are connected to it
	 * 
	 * @return
	 */
	private CompletableFuture<Void> closeConference() {
		logger.info("Closing conference");
		compFuture.complete(this);
		return this.<Void>toFuture(cb -> getAri().bridges().destroy(bridgeId, cb));
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
		return callController.answer().run()
				.thenCompose(answerRes-> this.<Void>toFuture(cb -> getAri().bridges().addChannel(bridgeId, newChannelId, "ConfUser", cb)))
				.thenCompose(v -> {
					logger.fine("Channel was added to the bridge");
					if (beep)
						return this.<Playback>toFuture(cb ->getAri().bridges().play(bridgeId, "sound:beep", "en", 0, 0,
								UUID.randomUUID().toString(),cb));
					else
						return CompletableFuture.completedFuture(null);
				})
				.thenCompose(pbRes->{
					channelIdsInConf.add(newChannelId);
					getArity().addFutureEvent(ChannelHangupRequest.class, newChannelId, this::removeAndCloseIfEmpty,
							true);
					if (mute)
						return callController.mute(newChannelId, "out").run();
					else
						return CompletableFuture.completedFuture(null);
				})
				.thenCompose(muteRes->annouceUser(newChannelId, "joined"))
					.thenCompose(pb -> {
						if (channelIdsInConf.size() == 1) {
							this.<Playback>toFuture(cb->getAri().bridges().play(bridgeId, "sound:conf-onlyperson", "en", 0, 0, UUID.randomUUID().toString(), cb))
							.thenCompose(playRes -> {
								logger.info("1 person in the conference");
								return startMusicOnHold(newChannelId).thenCompose(v2 -> {
									logger.info("Playing music to bridge with id " + bridgeId);
									compFuture.complete(this);
									return compFuture;
								});
							});
						}
						if (channelIdsInConf.size() == 2) {
							logger.info("2 channels are at conefernce " + confName + " , conference started");
							return stoptMusicOnHold().thenCompose(v3 -> {
								if (needToRecord) {
									logger.info("Start recording conference "+confName);
									if(Objects.equals(recordName, ""))
										recordName = UUID.randomUUID().toString();
									setConferenceRecord(callController.record(recordName, ".wav"));
									conferenceRecord.run().thenAccept(recordRes-> logger.fine("Finished recording"));
								}
								logger.info("stoped playing music on hold to the conference bridge");
								return compFuture;
							});
						}
						logger.fine("There are " + channelIdsInConf.size() + " channels in conference " + confName);
						return compFuture;
					})
				.exceptionally(t -> {
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
			removeChannelFromConf(hangup.getChannel().getId()).thenAccept(v1 -> {
				logger.info("Channel left conference " + confName);
				if (channelIdsInConf.isEmpty())
					closeConference().thenAccept(
							v2 -> logger.info("Nobody in the conference, closed the conference" + confName));
			});
		}
		logger.fine("Caller hang up, stop recording conference");
		conferenceRecord.stopRecording();
		return true;
	}

	/**
	 * Announce new channel joined/left a conference
	 * 
	 * @param newChannelId
	 *            channel id
	 * @param status
	 *            'joined' or 'left' conference
	 */
	private CompletableFuture<Playback> annouceUser(String newChannelId, String status) {
		String playbackId = UUID.randomUUID().toString();
		if (Objects.equals(status, "joined"))
			return this.<Playback>toFuture(
					cb -> getAri().bridges().play(bridgeId, "sound:confbridge-has-joined", "en", 0, 0, playbackId, cb));
		else
			return this.<Playback>toFuture(
					cb -> getAri().bridges().play(bridgeId, "sound:conf-hasleft", "en", 0, 0, playbackId, cb));
	}

	/**
	 * play music on hold to a channel that is alone in the conference
	 * 
	 * @param newChannelId
	 *            id of the channel
	 */
	private CompletableFuture<Void> startMusicOnHold(String newChannelId) {
		return this.<Void>toFuture(cb -> getAri().bridges().startMoh(bridgeId, "sound:pls-hold-while-try", cb))
				.exceptionally(t -> {
					logger.fine("Unable to start music on hold to channel " + newChannelId + " "
							+ ErrorStream.fromThrowable(t));
					return null;
				});
	}

	/**
	 * stop playing music on hold to the bridge
	 * 
	 */
	private CompletableFuture<Void> stoptMusicOnHold() {
		return this.<Void>toFuture(cb -> getAri().bridges().stopMoh(bridgeId, cb))
				.thenAccept(v -> logger.fine("stoped playing music on hold to conference bridge")).exceptionally(t -> {
					logger.fine("Unable to stop playing music on hold to the conference bridge with id :" + bridgeId
							+ " " + ErrorStream.fromThrowable(t));
					return null;
				});
	}

	/**
	 * remove channel that left the conference
	 * 
	 * @param channel
	 */
	public CompletableFuture<Void> removeChannelFromConf(String newChannelId) {
		return this
				.<Void>toFuture(cb -> getAri().bridges().removeChannel(bridgeId, newChannelId, new AriCallback<Void>() {
					@Override
					public void onSuccess(Void result) {
						channelIdsInConf.remove(newChannelId);
						if (channelIdsInConf.size() == 1)
							startMusicOnHold(newChannelId)
									.thenAccept(v -> logger.fine("Start music on hold to channel " + newChannelId));
						else
							annouceUser(newChannelId, "left").thenAccept(v -> logger.info("Announced that channel "
									+ newChannelId + " " + "has left conference " + confName));
					}

					@Override
					public void onFailure(RestException e) {
						logger.warning("Unable to remove channel from conference " + ErrorStream.fromThrowable(e));
					}
				})).thenAccept(res -> logger.fine("Channel was removed from conference")).exceptionally(t -> {
					logger.fine("Unable to remove channel from conference" + ErrorStream.fromThrowable(t));
					return null;
				});
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

	public Record getConferenceRecord() {
		return conferenceRecord;
	}

	public void setConferenceRecord(Record conferenceRecord) {
		this.conferenceRecord = conferenceRecord;
	}
}

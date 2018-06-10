package io.cloudonix.arity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ErrorStream;

/**
 * The class handles and saves all needed information for a conference call
 * 
 * @author naamag
 *
 */
public class Conference extends Operation {

	public enum ConferenceState {
		Destroyed, Destroying, Creating, Ready, ReadyWaiting, Muted, AdminMuted
	}

	private CompletableFuture<Conference> compFuture = new CompletableFuture<>();
	private ConferenceState currState;
	private Bridge confBridge;
	private String confName;
	// channel id's of all channels in the conference
	private List<String> channelIdsInConf = new CopyOnWriteArrayList<>();
	private CallController callController;
	private final static Logger logger = Logger.getLogger(Conference.class.getName());
	private String bridgeId = UUID.randomUUID().toString();

	/**
	 * Constructor
	 * 
	 * @param callController
	 *            callController instance
	 * @param name
	 *            name of the conference
	 */
	public Conference(CallController callController, String name) {
		super(callController.getCallState().getChannelID(), callController.getCallState().getArity(),
				callController.getCallState().getAri());
		this.callController = callController;
		this.confName = name;
		currState = ConferenceState.Creating;
	}

	@Override
	public CompletableFuture<Conference> run() {

		return createOrConnectConference().thenCompose(confBridge -> {
			if (!Objects.equals(currState, ConferenceState.Ready)) {
				logger.severe("Conference that is not ready");
				return null;
			}
			compFuture.complete(this);
			return compFuture;
		});
	}

	/**
	 * close conference when no users are connected to it
	 * 
	 * @return
	 */
	private CompletableFuture<Void> closeConference() {
		compFuture.complete(this);
		return this.toFuture(cb -> getAri().bridges().destroy(bridgeId, cb));

	}

	/**
	 * create bridge to the conference
	 * 
	 * @return
	 */
	private CompletableFuture<Bridge> createOrConnectConference() {
		CompletableFuture<Bridge> bridgeFuture = new CompletableFuture<Bridge>();

		try {
			List<Bridge> bridges = getAri().bridges().list();
			if (!bridges.isEmpty()) {
				for (Bridge bridge : bridges) {
					if (bridge.getId().equals(bridgeId)) {
						logger.info("Conference " + confName + " already exists");
						return CompletableFuture.completedFuture(null);
					}
				}
			}
		} catch (RestException e1) {
			logger.fine("Unable to get list of asterisk bridges " + ErrorStream.fromThrowable(e1));
		}

		logger.info("Creating conference " + confName);
		getAri().bridges().create("mixing", bridgeId, confName, new AriCallback<Bridge>() {
			@Override
			public void onSuccess(Bridge result) {
				confBridge = result;
				currState = ConferenceState.Ready;
				logger.info("Conference: " + confName + " is ready to use");
				callController.getCallState().addConference(confName, result.getId());
				bridgeFuture.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning(
						"Failed creating bridge for conference " + confName + " " + ErrorStream.fromThrowable(e));
				bridgeFuture.completeExceptionally(e);
			}
		});
		return bridgeFuture;
	}

	/**
	 * add channel to the conference
	 * 
	 * @param newChannelId
	 *            id of the new channel that we want to add to add to the conference
	 */
	public CompletableFuture<Void> addChannelToConf(String newChannelId) {

		if (!currState.equals(ConferenceState.Ready)) {
			logger.severe("Can not join channel to a conference that is not ready to use");
			CompletableFuture<Void> future = new CompletableFuture<Void>();
			future.completeExceptionally(new Exception());
			return future;
		}
		return this.<Void>toFuture(cb -> getAri().bridges().addChannel(confBridge.getId(), newChannelId, "ConfUser", cb))
				.thenAccept(v -> {
					logger.fine("Channel was added to the bridge");
					channelIdsInConf.add(newChannelId);

					getArity().addFutureEvent(ChannelHangupRequest.class, this::closeConfIfEmpty);
				}).thenAccept(v -> annouceUser(newChannelId, "joined")).thenCompose(pb -> {
					if (channelIdsInConf.size() == 1) {
						return callController.play("conf-onlyperson").run()
								.thenAccept(v2 -> logger.info("1 person in the conference"));
						/*
						 * .thenAccept(res -> startMusicOnHold(newChannelId))
						 * .thenAccept(v->logger.info("playing music on hold to the bridge "+
						 * bridgeId));
						 */
					}

					logger.fine("There are " + channelIdsInConf.size() + " channels in the conference");
					/*
					 * stoptMusicOnHold(newChannelId)
					 * .thenAccept(v->logger.info("stoped playing music on hold to the channel"));
					 */
					return CompletableFuture.completedFuture(null);
				});

	}

	private boolean closeConfIfEmpty(ChannelHangupRequest hangup) {
		if (!channelIdsInConf.contains(hangup.getChannel().getId()))
			return false;

		removeChannelFromConf(hangup.getChannel().getId()).thenAccept(v2 -> {
			logger.info("Channel left conference");
			if (channelIdsInConf.isEmpty()) {
				closeConference().thenAccept(v3 -> {
					logger.info("Nobody in the conference, closed the conference");
				});
			}
		});
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
		return this.<Void>toFuture(cb -> getAri().bridges().startMoh(bridgeId, "", cb)).exceptionally(t -> {
			logger.fine(
					"Unable to start music on hold to channel " + newChannelId + " " + ErrorStream.fromThrowable(t));
			return null;
		});
	}

	/**
	 * stop playing music on hold to the channel
	 * 
	 * @param newChannelId
	 *            id of the channel
	 */
	private CompletableFuture<Void> stoptMusicOnHold(String newChannelId) {
		return this.<Void>toFuture(cb -> getAri().channels().stopMoh(newChannelId, cb))
				.thenAccept(res -> logger.fine("stoped playing music on hold to the channel")).exceptionally(t -> {
					logger.fine("Unable to stop playing music on hold to the channel " + newChannelId + " "
							+ ErrorStream.fromThrowable(t));
					return null;
				});
	}

	/**
	 * remove channel that left the conference
	 * 
	 * @param channel
	 */
	public CompletableFuture<Void> removeChannelFromConf(String newChannelId) {
		return this.<Void>toFuture(
				cb -> getAri().bridges().removeChannel(confBridge.getId(), newChannelId, new AriCallback<Void>() {
					@Override
					public void onSuccess(Void result) {
						channelIdsInConf.remove(newChannelId);
						callController.getCallState().removeConference(confName);
						annouceUser(newChannelId, "left").thenAccept(v -> logger.info("Announced that channel "
								+ newChannelId + " " + "has left the conference " + confName));

						if (channelIdsInConf.size() == 1)
							startMusicOnHold(newChannelId)
									.thenAccept(v -> logger.fine("Start music on hold to channel " + newChannelId));
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
	 * create new channel in order to add it to conference
	 * 
	 * @param localChannelId
	 * @param otherChannelId
	 */
	public CompletableFuture<Channel> createtChannel(String localChannelId, String otherChannelId) {
		getArity().ignoreChannel(localChannelId);
		return this.<Channel>toFuture(cb -> getAri().channels().create("Local/" + localChannelId,
				getArity().getAppName(), null, localChannelId, null, otherChannelId, null, cb));
	}

	/**
	 * get conference current state
	 * 
	 * @return
	 */
	public ConferenceState getCurrState() {
		return currState;
	}

	/**
	 * get conference bridge
	 * 
	 * @return
	 */
	public Bridge getConfBridge() {
		return confBridge;
	}

	/**
	 * set conference bridge
	 * 
	 * @return
	 */
	public void setConfBridge(Bridge confBridge) {
		this.confBridge = confBridge;
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

}

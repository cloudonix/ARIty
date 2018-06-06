package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
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
	private int count = 0;
	// channel id's of all channels in the conference
	private List<String> channelIdsInConf;
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
		channelIdsInConf = new ArrayList<>();
		currState = ConferenceState.Creating;
	}

	@Override
	public CompletableFuture<Conference> run() {

		return createConferenceBridge()
				.thenCompose(confBridge -> {
			if (!Objects.equals(currState, ConferenceState.Ready)) {
				logger.severe("cannot join to conference that is not ready");
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
	private CompletableFuture<Bridge> createConferenceBridge() {
		CompletableFuture<Bridge> bridgeFuture = new CompletableFuture<Bridge>();

		getAri().bridges().create("mixing", bridgeId, confName, new AriCallback<Bridge>() {
			@Override
			public void onSuccess(Bridge result) {
				confBridge = result;
				currState = ConferenceState.Ready;
				logger.info("conference: " + confName + " is ready");
				callController.getCallState().addConference(confName, result.getId());
				bridgeFuture.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("failed creating bridge for conference " + ErrorStream.fromThrowable(e));
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
			logger.severe("can not join to conference that is not ready");
			return CompletableFuture.completedFuture(null);
		}

		return this.<Void>toFuture(
				res -> getAri().bridges().addChannel(confBridge.getId(), newChannelId, "join", new AriCallback<Void>() {
					@Override
					public void onSuccess(Void result) {
						logger.info("channel was added to conference");
						channelIdsInConf.add(newChannelId);
						count++;

						getArity().addFutureEvent(ChannelHangupRequest.class, (hangup) -> {
							if (channelIdsInConf.contains(hangup.getChannel().getId())) {
								removeChannelFromConf(hangup.getChannel().getId()).thenAccept(v -> {
									logger.info("channel left conference");
									if (count == 0) {
										closeConference().thenAccept(v3 -> {
											logger.info("no one left in the conference, closing conference");
										});
									}
								});
								return true;
							}
							return false;
						});

						// record name of the added channel and announce on it
						callController.play("priv-recordintro").run()
								.thenCompose(res -> callController.record(newChannelId, "wav").run())
								.thenAccept(record -> annouceUser(newChannelId, "joined"));

						if (count == 1) {
							logger.info("1 person in the conference");
							callController.play("conf-onlyperson").run()
									.thenAccept(res -> startMusicOnHold(newChannelId));
							return;
						}

						if (count >= 2) {
							logger.fine("there at least 2 channels in the conference");
							stoptMusicOnHold(newChannelId);
						}
					}

					@Override
					public void onFailure(RestException e) {
						logger.warning("channel was not added to the conference: " + ErrorStream.fromThrowable(e));
					}
				}));
	}

	/**
	 * Announce new channel joined/left a conference
	 * 
	 * @param newChannelId
	 *            channel id
	 * @param status
	 *            'joined' or 'left' conference
	 */
	private CompletableFuture<Void> annouceUser(String newChannelId, String status) {
		return callController.playRecording(callController.getCallState().getConfBridgeId(confName), newChannelId).run()
				.thenAccept(playToBridge -> {
					PlayToBridge pb;
					if (Objects.equals(status, "joined"))
						pb = callController.play(callController.getCallState().getConfBridgeId(confName),
								"confbridge-has-joined");
					else
						pb = callController.play(callController.getCallState().getConfBridgeId(confName),
								"conf-hasleft");
					pb.run();
				}).thenCompose(v -> {
					logger.info("done announcing channel in conference");
					return CompletableFuture.completedFuture(v);
				}).exceptionally(t -> {
					logger.info("unable to announce channel in conference: " + ErrorStream.fromThrowable(t));
					return null;
				});
	}

	/**
	 * play music on hold to a channel that is alone in the conference
	 * 
	 * @param newChannelId
	 *            id of the channel
	 */
	private CompletableFuture<Void> startMusicOnHold(String newChannelId) {
		return this.<Void>toFuture(cb -> getAri().channels().startMoh(newChannelId, "default", cb)).exceptionally(t -> {
			logger.fine("unable to start music on hold" + ErrorStream.fromThrowable(t));
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
					logger.fine("unable to stop playing music on hold to the channel" + ErrorStream.fromThrowable(t));
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
						count--;
						channelIdsInConf.remove(newChannelId);
						callController.getCallState().removeConference(confName);
						annouceUser(newChannelId, "left");

						if (count == 1)
							startMusicOnHold(newChannelId);
					}

					@Override
					public void onFailure(RestException e) {
						logger.warning("unable to remove channel from conference " + ErrorStream.fromThrowable(e));
					}
				})).thenAccept(res -> logger.fine("channel was removed from conference")).exceptionally(t -> {
					logger.fine("unable to remove channel from conference" + ErrorStream.fromThrowable(t));
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
		return this
				.<Channel>toFuture(cb -> getAri().channels().create("Local/" +confName, getArity().getAppName(), null,
						localChannelId, null, otherChannelId, null, cb));

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
		return count;
	}

	/**
	 * set number of channels in conference
	 * 
	 * @return
	 */
	public void setCount(int count) {
		this.count = count;
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

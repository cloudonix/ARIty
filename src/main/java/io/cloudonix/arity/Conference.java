package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.ChannelLeftBridge_impl_ari_2_0_0;
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

	private CompletableFuture<Conference> compFuture;
	private ConferenceState currState;
	private Bridge confBridge;
	private String confName;
	private int count = 0;
	// channel id's of all channels in the conference
	private List<String> channelIdsInConf;
	private String channelId;
	private CallController callController;
	private final static Logger logger = Logger.getLogger(Conference.class.getName());
	private String bridgeId = UUID.randomUUID().toString();

	/**
	 * Constructor
	 * 
	 * @param channeId
	 *            id of the channel
	 * @param arity
	 *            instance of ARIty
	 * @param ari
	 *            instance of ARI
	 * @param name
	 *            name of the conference
	 */
	public Conference(CallController callController, String name) {
		super(callController.getCallState().getChannelID(), callController.getCallState().getArity(),
				callController.getCallState().getAri());
		this.channelId = callController.getCallState().getChannelID();
		this.callController = callController;
		this.confName = name;
		channelIdsInConf = new ArrayList<>();
		compFuture = new CompletableFuture<>();
		currState = ConferenceState.Creating;
		callController.getAri().bridges().create("mixing",bridgeId, confName, new AriCallback<Bridge>() {
			@Override
			public void onSuccess(Bridge result) {
				confBridge = result;
				currState = ConferenceState.Ready;
				logger.info("conference: " + confName + "is ready");
				callController.getCallState().addConference(confName, result.getId());
				channelIdsInConf.add(result.getId());
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("failed creating bridge for conference " + ErrorStream.fromThrowable(e));
			}
		});
	}

	@Override
	public CompletableFuture<Conference> run() {
		if (Objects.equals(currState, ConferenceState.Ready)) {
			return this.<Void>toFuture(addChannel -> addChannelToConf(channelId)).thenAccept(v -> {
				for (int i = 0; i < channelIdsInConf.size(); i++) {

					getArity().addFutureEvent(ChannelHangupRequest.class, (hangup) -> {
						if (channelIdsInConf.contains(hangup.getChannel().getId())) {

							getArity().addFutureEvent(ChannelLeftBridge_impl_ari_2_0_0.class, (chanLeftBridge) -> {
								if (channelIdsInConf.contains(chanLeftBridge.getChannel().getId())) {
									removeChannelFromConf(chanLeftBridge.getChannel().getId());
									if (count == 0)
										compFuture.complete(this);
									return true;
								}
								return false;
							});
							return true;
						}
						return false;
					});
				}
			}).thenCompose(v -> compFuture);
		}
		logger.severe("cannot join to conference that is not ready");
		return null;
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

	/**
	 * add channel to the conference
	 * 
	 * @param newChannelId
	 *            id of the new channel that we want to add to add to the conference
	 */
	public void addChannelToConf(String newChannelId) {

		if (!currState.equals(ConferenceState.Ready)) {
			logger.severe("can not join to conference that is not ready");
			return;
		}

		getAri().bridges().addChannel(confBridge.getId(), newChannelId, "join", new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.info("channel was added to conference");
				channelIdsInConf.add(newChannelId);
				count++;
				// record name of the added channel and announce on it
				callController.play("priv-recordintro").run()
						.thenCompose(res -> callController.record(newChannelId, "wav").run())
						.thenAccept(res -> annouceUser(newChannelId, "joined"));
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("channel was not added to the conference: " + ErrorStream.fromThrowable(e));
			}
		});

		if (count == 1)
			callController.play("conf-onlyperson").run().thenAccept(res -> startMusicOnHold(newChannelId));

		if (count >= 2) {
			logger.fine("there at least 2 channels in the conference");
			stoptMusicOnHold(newChannelId);
		}
	}

	/**
	 * Announce new channel joined/left a conference
	 * 
	 * @param newChannelId
	 *            channel id
	 * @param status
	 *            'joined' or 'left' conference
	 */
	private void annouceUser(String newChannelId, String status) {
		callController.playRecording(callController.getCallState().getConfBridgeId(confName), newChannelId).run()
				.thenAccept(res -> {
					PlayToBridge pb;
					if (Objects.equals(status, "joined"))
						pb = callController.play(callController.getCallState().getConfBridgeId(confName),
								"confbridge-has-joined");
					else
						pb = callController.play(callController.getCallState().getConfBridgeId(confName),
								"conf-hasleft");
					pb.run();
				}).thenAccept(v -> logger.info("done announcing channel in conference"))
				.exceptionally(t -> {
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
	private void startMusicOnHold(String newChannelId) {
		getAri().channels().startMoh(newChannelId, "default", new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.fine("playing music on hold to channel: " + newChannelId);
			}

			@Override
			public void onFailure(RestException e) {
				logger.fine("unable to play music on hold to channel: " + ErrorStream.fromThrowable(e));
			}
		});
	}

	/**
	 * stop playing music on hold to the channel
	 * 
	 * @param newChannelId
	 *            id of the channel
	 */
	private void stoptMusicOnHold(String newChannelId) {
		getAri().channels().stopMoh(newChannelId, new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				logger.fine("stoped music on hold to channel " + newChannelId);
			}

			@Override
			public void onFailure(RestException e) {
				logger.fine("unable to stop music to channel " + newChannelId + " " + ErrorStream.fromThrowable(e));
			}
		});
	}

	/**
	 * remove channel that left the conference
	 * 
	 * @param channel
	 */
	public void removeChannelFromConf(String newChannelId) {
		getAri().bridges().removeChannel(confBridge.getId(), newChannelId, new AriCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				count--;
				channelIdsInConf.remove(newChannelId);
				callController.getCallState().removeConference(confName);
				annouceUser(newChannelId, "left");

				if (count <= 1)
					startMusicOnHold(newChannelId);

				if (count == 0) {
					logger.info("conference is empty, closing conference");
					currState = ConferenceState.Destroying;
					getAri().bridges().destroy(confBridge.getId(), new AriCallback<Void>() {
						@Override
						public void onSuccess(Void result) {
							logger.info("closed conference " + confName);
							confBridge = null;
						}

						@Override
						public void onFailure(RestException e) {
							logger.warning("unable to close conference: " + ErrorStream.fromThrowable(e));
						}
					});
				}
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("unable to remove channel from conference " + ErrorStream.fromThrowable(e));
			}
		});

	}

}

package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.ChannelLeftBridge_impl_ari_2_0_0;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ErrorStream;
/**
 * The class handles and saves all needed information for a conference call
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
	private List<Channel> channelsInConf;
	private Channel newChannel = null;
	private final static Logger logger = Logger.getLogger(Conference.class.getName());

	public Conference(String id, ARIty arity, ARI ari) {
		super(id, arity, ari);
		channelsInConf = new ArrayList<>();
		compFuture = new CompletableFuture<>();
		currState = ConferenceState.Creating;
	}

	@Override
	public CompletableFuture<? extends Operation> run() {
		if (Objects.equals(currState, ConferenceState.Ready)) {
			return this.<Void>toFuture(addChannel -> addChannelToConf(newChannel)).thenAccept(v -> {
				for (int i = 0; i < channelsInConf.size(); i++) {
					// notice when a channel left a conference bridge
					getArity().addFutureEvent(ChannelLeftBridge_impl_ari_2_0_0.class, (chanLeftBridge) -> {
						if (channelsInConf.contains(chanLeftBridge.getChannel())) {
							removeChannelFromConf(chanLeftBridge.getChannel());
							if (channelsInConf.size() < 2) {
								logger.info("conference must contain at least 2 participantes. closing the conference");
								// update the list of active conferences
								currState = ConferenceState.Destroying;
								List<Conference> conferences = getArity().getCallSupplier().get().getConferences();
								conferences.remove(this);
								getArity().getCallSupplier().get().setConferences(conferences);
								compFuture.complete(this);
							}
							return true;
						}
						return false;
					});
				}
			}).thenCompose(v -> compFuture);
		}
		logger.severe("cannot join conference that is not ready");
		return null;
	}

	/**
	 * get conference currenr state
	 * 
	 * @return
	 */
	public ConferenceState getCurrState() {
		return currState;
	}

	/**
	 * set conference state
	 * 
	 * @param currState
	 *            update sate of conference
	 */
	public void setCurrState(ConferenceState currState) {
		this.currState = currState;
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
	public List<Channel> getChannelsInConf() {
		return channelsInConf;
	}

	/**
	 * set list of channels connected to the conference bridge
	 * 
	 * @return
	 */
	public void setChannelsInConf(List<Channel> channelsInConf) {
		this.channelsInConf = channelsInConf;
	}

	/**
	 * get the channel we want to add to the conference
	 * 
	 * @return
	 */
	public Channel getNewChannel() {
		return newChannel;
	}

	/**
	 * set the channel we want to add to the conference
	 * 
	 * @param newChannel
	 */
	public void setNewChannel(Channel newChannel) {
		this.newChannel = newChannel;
	}

	/**
	 * add channel to the conference
	 * 
	 * @param channel
	 * @param cc
	 */
	public void addChannelToConf(Channel channel) {
		try {
			getAri().bridges().addChannel(confBridge.getId(), channel.getId(), "join");
		} catch (RestException e) {
			logger.severe("unable to add channel to conference " + ErrorStream.fromThrowable(e));
			return;
		}
		channelsInConf.add(channel);
		count++;

		if (count == 1) {
			logger.info("channel joined to conference and it is the only in it");
			// new Play(arity.getCallSupplier().get(), "conf-onlyperson").run();
		} else {
			logger.info("channel joined to conference");
			// play to all channels in the bridge- new PlayBridge class and in run method:
			// getAri().bridges().play(getBridgeId(), fullPath,
			// callStasisStart.getChannel().getLanguage(), 0, 0,playbackId, new
			// AriCallback<Playback>() {
		}
		if (count == 2)
			currState = ConferenceState.Ready;
	}

	/**
	 * remove channel that left the conference
	 * 
	 * @param channel
	 */
	public void removeChannelFromConf(Channel channel) {
		count--;
		channelsInConf.remove(channel);
		// conference can't contain less than 2 participants
		if (count < 2) {
			currState = ConferenceState.Destroying;
			compFuture.complete(this);
		}
	}

}

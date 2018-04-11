package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ErrorStream;

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
	private String confId;
	private final static Logger logger = Logger.getLogger(Conference.class.getName());

	public Conference(String confId, ARIty arity, ARI ari) {
		super(confId, arity, ari);
		this.confId = confId;
		channelsInConf = new ArrayList<>();
		compFuture = new CompletableFuture<>();
	}

	@Override
	public CompletableFuture<? extends Operation> run() {
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
	 * add channel to the conference
	 * 
	 * @param channel
	 * @param cc
	 */
	public void addChannelToConf(Channel channel, CallController cc) {
		
		if (Objects.equals(currState, ConferenceState.Ready)) {
			try {
				cc.getAri().bridges().addChannel(confBridge.getId(), channel.getId(), "joining conference");
			} catch (RestException e) {
				logger.severe("unable to add channel to conference " + ErrorStream.fromThrowable(e));
				return;
			}
			channelsInConf.add(channel);
			count++;
			if (count == 1) {
				logger.info("channel joind to conference and it is the only one channel in it");
				new Play(cc, "conf-onlyperson").run();
			} else {
				logger.info("channel joind to conference");
				// play to all channels in the bridge- new PlayBridge class and in run method:
				// getAri().bridges().play(getBridgeId(), fullPath,
				// callStasisStart.getChannel().getLanguage(), 0, 0,playbackId, new
				// AriCallback<Playback>() {
			}
			if (count == 2)
				currState = ConferenceState.Ready;
		}
		else
			logger.info("unable to add channel to conference. conference state is: " + currState);
	}

	/**
	 * remove channel that left the conference
	 * 
	 * @param channel
	 *            channel to be removed
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

package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.Channel;

public class Conf extends Operation {
	
	public enum ConferenceState
    {
        Destroyed, Destroying ,Creating, Ready, ReadyWaiting, Muted, AdminMuted 
    }
	private ARIty arity;
	private ARI ari;
	private ConferenceState currState;
	private Bridge confBridge;
	private String confName;
	private int count = 0;
	private List<Channel> channelsInConf;
	private final static Logger logger = Logger.getLogger(Conf.class.getName());

	
	public Conf(String channelId, ARIty arity, ARI ari) {
		super(channelId, arity, ari);
		this.arity = arity;
		this.ari = ari;
		channelsInConf = new ArrayList<>();
	}

	@Override
	public CompletableFuture<? extends Operation> run() {
		return null;
	}
	
	public ConferenceState getCurrState() {
		return currState;
	}

	public void setCurrState(ConferenceState currState) {
		this.currState = currState;
	}
	public Bridge getConfBridge() {
		return confBridge;
	}

	public void setConfBridge(Bridge confBridge) {
		this.confBridge = confBridge;
	}

	public String getConfName() {
		return confName;
	}

	public void setConfName(String confName) {
		this.confName = confName;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public List<Channel> getChannelsInConf() {
		return channelsInConf;
	}

	public void setChannelsInConf(List<Channel> channelsInConf) {
		this.channelsInConf = channelsInConf;
	}
	
	public void addChannelToConf (Channel channel, CallController cc) {
		channelsInConf.add(channel);
		count++;
		if(count == 1) {
			logger.info("channel joind to conference and it is the only one channel in it");
			new Play(cc, "conf-onlyperson").run();
		}
		else {
			logger.info("channel joind to conference");
			//play to all channels in the bridge- new PlayBridge class and in run method: 
			//getAri().bridges().play(getBridgeId(), fullPath, callStasisStart.getChannel().getLanguage(), 0, 0,playbackId, new AriCallback<Playback>() {
		}
		if(count == 2)
			currState = ConferenceState.Ready;
	}


}

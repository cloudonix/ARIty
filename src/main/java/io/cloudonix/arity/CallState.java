package io.cloudonix.arity;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelVarset;
import ch.loway.oss.ari4java.generated.StasisStart;

/**
 * class that holds data of the call
 * 
 * @author naamag
 *
 */
public class CallState {

	private StasisStart callStasisStart;
	private ARI ari;
	private String channelID;
	private ARIty arity;
	private Channel channel;
	private String channelTechnology;
	private Map<String, Object> metaData = new HashMap<>();
	private Logger logger = Logger.getLogger(getClass().getName());
	private List<ChannelVarset> allChannelVarSet = Collections.emptyList();

	public CallState(StasisStart callStasisStart, ARI ari, ARIty arity, String channelID, Channel channel,
			String channelTechnology) {
		this.ari = ari;
		this.arity = arity;
		this.channelID = channelID;
		this.channel = channel;
		this.channelTechnology = channelTechnology;
		this.callStasisStart = callStasisStart;
		arity.addFutureEvent(ChannelVarset.class, channelID, this::saveChannelVarset, false);
		logger = Logger.getLogger(getClass().getName() + ":" + channelID);
	}

	/**
	 * save ChannelVarset events
	 * 
	 * @param cvs ChannelVarset event
	 * @return
	 */
	public Boolean saveChannelVarset(ChannelVarset cvs) {
		logger.info("Got ChannelVarset. Variable " + cvs.getVariable() + " has changed and new value is: "
				+ cvs.getValue());
		allChannelVarSet.add(cvs);
		return false;
	}

	public StasisStart getCallStasisStart() {
		return callStasisStart;
	}

	public ARI getAri() {
		return ari;
	}

	public String getChannelID() {
		return channelID;
	}

	public ARIty getArity() {
		return arity;
	}

	public Channel getChannel() {
		return channel;
	}

	public Map<String, Object> getMetaData() {
		return metaData;
	}

	/**
	 * add data to call state
	 * 
	 * @param dataName    name representing the data
	 * @param dataContent object that contains the data
	 */
	public void put(String dataName, Object dataContent) {
		metaData.put(dataName, dataContent);
	}

	/**
	 * get the needed data from the state of the call
	 * 
	 * @param dataName name of the data
	 * @param class1   class representing the data
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(String dataName) {
		return (T) metaData.get(dataName);
	}

	/**
	 * get channel technology. ex: SIP, PJSIP
	 * 
	 * @return
	 */
	public String getChannelTechnology() {
		return channelTechnology;
	}

	public List<ChannelVarset> getLatestChannelVarSet() {
		return allChannelVarSet;
	}

	/**
	 * get the variable's value of the channel
	 * 
	 * @param variable name of the variable
	 * @return the value of the variable if exists, or "Not found" otherwise
	 */
	public String getValueOfVariable(String variable) {
		for (ChannelVarset cvs : allChannelVarSet) {
			if (Objects.equals(variable, cvs.getVariable()))
				return cvs.getValue();
		}
		return "Not found";
	}
}

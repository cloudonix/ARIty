package io.cloudonix.arity;

import java.util.HashMap;
import java.util.Map;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.StasisStart;

/**
 * class that holds data about the call
 * 
 * @author naamag
 *
 */
public class CallState {

	private StasisStart callStasisStart;
	private ARI ari;
	private String channelId;
	private ARIty arity;
	private Channel channel;
	private String channelTechnology;
	private Map<String, Object> metaData = new HashMap<>();

	public CallState(StasisStart callStasisStart, ARI ari, ARIty arity, String channelID, Channel channel,
			String channelTechnology) {
		this.ari = ari;
		this.arity = arity;
		this.channelId = channelID;
		this.channel = channel;
		this.channelTechnology = channelTechnology;
		this.callStasisStart = callStasisStart;
	}

	public CallState(ARI ari, ARIty arity) {
		this.ari = ari;
		this.arity = arity;
	}

	public StasisStart getCallStasisStart() {
		return callStasisStart;
	}

	public ARI getAri() {
		return ari;
	}

	public String getChannelId() {
		return channelId;
	}

	public ARIty getArity() {
		return arity;
	}

	public Channel getChannel() {
		return channel;
	}
	
	public void setChannel(Channel channel) {
		this.channel = channel;
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

	public void setChannelId(String channelId) {
		this.channelId = channelId;
	}

	public void setChannelTechnology(String channelTechnology) {
		this.channelTechnology = channelTechnology;
	}
}

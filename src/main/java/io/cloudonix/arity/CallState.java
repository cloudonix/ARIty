package io.cloudonix.arity;

import java.util.HashMap;
import java.util.Map;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelVarset;
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
	private Map<String, String> variables = new HashMap<>();

	public CallState(StasisStart callStasisStart, ARIty arity) {
		this.ari = arity.getAri();
		this.arity = arity;
		this.channel = callStasisStart.getChannel();
		this.channelId = channel.getId();
		this.callStasisStart = callStasisStart;
		this.channelTechnology = channel.getName().split("/")[0];
		arity.addFutureEvent(ChannelVarset.class, channelId, (varset, se) -> {
			variables.put(varset.getVariable(), varset.getValue());
		});
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
	 * Store custom data in the transferable call state
	 * @param dataName    name of the data field to store
	 * @param dataContent Data to store
	 */
	public void put(String dataName, Object dataContent) {
		metaData.put(dataName, dataContent);
	}

	/**
	 * Load custom data from the transferable call state
	 * 
	 * The data will be cast to the expected data type, so make sure you always store and load the same type
	 * for the same field name
	 * 
	 * @param dataName name of the data field to load
	 * @return the value stored, casted to the expected type
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(String dataName) {
		return (T) metaData.get(dataName);
	}
	
	/**
	 * Check if specific custom data field was stored in the transferable call state
	 * @param dataName name of the data field to check
	 * @return Whether the field has been previously stored in the call state, even if its value was stored as <tt>null</tt>
	 */
	public boolean contains(String dataName) {
		return metaData.containsKey(dataName);
	}
	
	/**
	 * Retrieve an asterisk variable that was set on the current channel
	 * @param name variable name to read
	 * @return variable value
	 */
	public String getVar(String name) {
		return variables.get(name);
	}

	/**
	 * The Asterick channel technology for the current channel. ex: SIP, PJSIP
	 * @return
	 */
	public String getChannelTechnology() {
		return channelTechnology;
	}

}

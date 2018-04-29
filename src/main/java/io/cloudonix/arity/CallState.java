package io.cloudonix.arity;

import java.util.Map;
import java.util.Objects;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Channel;
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
	private Map<String, Object> metaData;

	public StasisStart getCallStasisStart() {
		return callStasisStart;
	}

	public void setCallStasisStart(StasisStart callStasisStart) {
		this.callStasisStart = callStasisStart;
	}

	public ARI getAri() {
		return ari;
	}

	public void setAri(ARI ari) {
		this.ari = ari;
	}

	public String getChannelID() {
		return channelID;
	}

	public void setChannelID(String channelID) {
		this.channelID = channelID;
	}

	public ARIty getArity() {
		return arity;
	}

	public void setArity(ARIty arity) {
		this.arity = arity;
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

	public void setMetaData(Map<String, Object> metaData) {
		this.metaData = metaData;
	}

	/**
	 * add data to call state
	 * 
	 * @param dataName
	 *            name representing the data
	 * @param dataContent
	 *            object that contains the data
	 */
	public void put(String dataName, Object dataContent) {
		metaData.put(dataName, dataContent);
	}

	/**
	 * get the needed data from the state of the call
	 * 
	 * @param dataName
	 *            name of the data
	 * @param class1
	 *            class representing the data
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(String dataName, Class<T> class1) {
		for (Map.Entry<String, Object> entry : metaData.entrySet()) {
			if (Objects.equals(dataName, entry.getKey()))
				return (T) entry.getValue();
		}
		return null;
	}

}

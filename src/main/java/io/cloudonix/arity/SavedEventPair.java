package io.cloudonix.arity;

import java.util.function.Function;

import ch.loway.oss.ari4java.generated.Message;

/**
 * The class represents saved events as a pair of function and channel id
 * 
 * @author naamag
 *
 */
public class SavedEventPair {

	private Function<Message, Boolean> func;
	private String channelId;

	public SavedEventPair(String channelId, Function<Message, Boolean> func) {
		this.channelId = channelId;
		this.func = func;
	}

	public Function<Message, Boolean> getFunc() {
		return func;
	}

	public String getChannelId() {
		return channelId;
	}

}

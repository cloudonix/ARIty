package io.cloudonix.arity;

import java.util.function.Function;

import ch.loway.oss.ari4java.generated.Message;

/**
 * The class represents saved events with function channel id, and if this events occurs one or many times
 * 
 * @author naamag
 *
 */
public class SavedEvent {

	private Function<Message, Boolean> func;
	private String channelId;
	private boolean runOnce;

	public SavedEvent(String channelId, Function<Message, Boolean> func, boolean runOnce) {
		this.channelId = channelId;
		this.func = func;
		this.runOnce = runOnce;
	}

	public Function<Message, Boolean> getFunc() {
		return func;
	}

	public String getChannelId() {
		return channelId;
	}

	public boolean isRunOnce() {
		return runOnce;
	}
}

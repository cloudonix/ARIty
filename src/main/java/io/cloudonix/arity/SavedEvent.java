package io.cloudonix.arity;

import java.util.function.Function;

import ch.loway.oss.ari4java.generated.Message;

/**
 * The class represents saved events with function channel id, and if this
 * events occurs one or many times
 * 
 * @author naamag
 *
 */
public class SavedEvent {

	private Function<Message, Boolean> func;
	private String channelId;

	/**
	 * Constructor
	 * 
	 * @param channelId id of the channel the event is registered for
	 * @param func      function to execute when the event arrives
	 * @param runOnce   true if the event arrives one time, false otherwise (for
	 *                  example: ChannelHangupRequest is an event that arrives only
	 *                  once)
	 */
	public SavedEvent(String channelId, Function<Message, Boolean> func) {
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

package io.cloudonix.arity;

import java.util.function.Consumer;

import ch.loway.oss.ari4java.generated.models.Message;

/**
 * An event listener that is registered to accept and dispatch events for a specific message type, optionally on a specific channel.
 * @author naamag
 * @author odeda
 */
public class OnetimeEventHandler<T extends Message> extends EventHandler<T> {

	private Consumer<T> handler;

	/**
	 * Create a new one-time event handler
	 *
	 * @param channelId id of the channel the event is registered for, or null if listening to a global event
	 * @param handler function to execute when the event arrives
	 * @param type message type
	 * @param arity owner ARIty instance
	 */
	OnetimeEventHandler(String channelId, Consumer<T> handler, Class<T> type, ARIty arity) {
		super(channelId, (t,se) -> { se.unregister(); handler.accept(t); }, type, arity);
		this.handler = handler;
	}

	@Override
	public String toString() {
		return "Event handler<" + clazz.getSimpleName() + ">[" + channelId + "]->" + makeName(handler.getClass());
	}

}

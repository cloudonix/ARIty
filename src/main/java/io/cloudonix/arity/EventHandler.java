package io.cloudonix.arity;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.models.Message;

/**
 * An event listener that is registered to accept and dispatch events for a specific message type, optionally on a specific channel.
 * @author naamag
 * @author odeda
 */
public class EventHandler<T extends Message> implements Consumer<T> {
	private BiConsumer<T, EventHandler<T>> handler;
	private String channelId;
	private Class<T> clazz;
	private ARIty arity;
	private volatile boolean registered = true;
	private final static Logger logger = LoggerFactory.getLogger(ARIty.class);

	/**
	 * Create a new event handler
	 *
	 * @param channelId id of the channel the event is registered for, or null if listening to a global event
	 * @param handler function to execute when the event arrives
	 * @param type message type
	 * @param arity owner ARIty instance
	 */
	EventHandler(String channelId, BiConsumer<T, EventHandler<T>> handler, Class<T> type, ARIty arity) {
		this.channelId = channelId;
		this.handler = handler;
		this.clazz = type;
		this.arity = arity;
	}

	/**
	 * Check the channel ID this event is listening on.
	 * @return channel ID for channel specific listeners, <code>null</code> for global listeners
	 */
	public String getChannelId() {
		return channelId;
	}

	/**
	 * Unregister from listening to this event
	 */
	public void unregister() {
		registered = false;
		arity.removeEventHandler(this);
	}

	@Override
	public void accept(Message m) {
		if (!registered || !clazz.isInstance(m))
			return;
		logger.debug("Triggering " + this);
		arity.dispatchTask(() -> handler.accept(clazz.cast(m), this));
	}

	@Override
	public String toString() {
		return "Event handler<" + clazz.getSimpleName() + ">[" + channelId + "]->" + handler.getClass();
	}
}

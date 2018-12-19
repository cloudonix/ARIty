package io.cloudonix.arity;

import java.util.function.BiConsumer;

import ch.loway.oss.ari4java.generated.Message;
import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Consumer;

/**
 * The class represents saved events with function channel id, and if this
 * events occurs one or many times
 * 
 * @author naamag
 *
 */
public class SavedEvent<T extends Message> implements Consumer<T> {
	private BiConsumer<T, SavedEvent<T>> handler;
	private String channelId;
	private Class<T> class1;
	private ARIty arity;

	/**
	 * Constructor
	 * 
	 * @param channelId   id of the channel the event is registered for
	 * @param futureEvent function to execute when the event arrives
	 */
	public SavedEvent(String channelId, BiConsumer<T, SavedEvent<T>> futureEvent, Class<T> class1, ARIty arity) {
		this.channelId = channelId;
		this.handler = futureEvent;
		this.class1 = class1;
		this.arity = arity;
	}

	/**
	 * get the channel id that the event was saved for
	 * 
	 * @return
	 */
	public String getChannelId() {
		return channelId;
	}

	/**
	 * unregister from listening to this saved event
	 */
	public void unregister() {
		arity.removeFutureEvent(this);
	}

	@Override
	public void accept(T m) {
		if (class1.isInstance(m))
			handler.accept((T) m, this);
	}
}

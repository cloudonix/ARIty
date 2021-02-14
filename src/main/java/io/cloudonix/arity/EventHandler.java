package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.models.Message;
import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Consumer;

/**
 * The class represents saved events with function channel id, and if this
 * events occurs one or many times
 *
 * @author naamag
 *
 */
public class EventHandler<T extends Message> implements Consumer<T> {
	private BiConsumer<T, EventHandler<T>> handler;
	private String channelId;
	private Class<T> class1;
	private ARIty arity;
	private final static Logger logger = Logger.getLogger(ARIty.class.getName());
	private final static AtomicInteger instanceCount = new AtomicInteger(0);
	private final int instanceId = instanceCount.getAndIncrement();

	/**
	 * Constructor
	 *
	 * @param channelId   id of the channel the event is registered for
	 * @param futureEvent function to execute when the event arrives
	 */
	public EventHandler(String channelId, BiConsumer<T, EventHandler<T>> futureEvent, Class<T> class1, ARIty arity) {
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
		arity.removeEventHandler(this);
	}

	@Override
	public void accept(Message m) {
		if (!class1.isInstance(m))
			return;
		logger.finest("Triggering " + this);
		CompletableFuture.runAsync(() -> handler.accept(class1.cast(m), this));
	}

	public Class<T> getClass1() {
		return class1;
	}

	@Override
	public String toString() {
		return "Event handler:" + class1.getSimpleName() + "[" + channelId + "]:" + handler.getClass();
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof EventHandler && instanceId == ((EventHandler<?>)obj).instanceId;
	}
	
	@Override
	public int hashCode() {
		return instanceId;
	}
}

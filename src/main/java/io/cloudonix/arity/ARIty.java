package io.cloudonix.arity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.Message;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.Channel_impl_ari_2_0_0;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.Dial_impl_ari_2_0_0;
import ch.loway.oss.ari4java.tools.ARIException;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ConnectionFailedException;
import io.cloudonix.arity.errors.ErrorStream;

/**
 * The class represents the creation of ari and websocket service that handles
 * the incoming events
 * 
 * @author naamag
 *
 */
public class ARIty implements AriCallback<Message> {

	private final static Logger logger = Logger.getLogger(ARIty.class.getName());
	private Queue<Function<Message, Boolean>> futureEvents = new ConcurrentLinkedQueue<Function<Message, Boolean>>();
	private ARI ari;
	private String appName;
	private Supplier<CallController> callSupplier = this::hangupDefault;
	// save the channel id of new calls (for ignoring another stasis start event, if
	// needed)
	private ConcurrentSkipListSet<String> ignoredChannelIds = new ConcurrentSkipListSet<>();
	private Exception lastException = null;

	/**
	 * Constructor
	 * 
	 * @param uri
	 *            URI
	 * @param name
	 *            name of the stasis application
	 * @param login
	 *            user name
	 * @param pass
	 *            password
	 * 
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 */
	public ARIty(String uri, String name, String login, String pass)
			throws ConnectionFailedException, URISyntaxException {
		appName = name;

		try {
			ari = ARI.build(uri, appName, login, pass, AriVersion.ARI_2_0_0);
			logger.info("ari created");
			logger.info("ari version: " + ari.getVersion());
			ari.events().eventWebsocket(appName, true, this);
			logger.info("websocket is open");
		} catch (ARIException e) {
			logger.severe("connection failed: " + ErrorStream.fromThrowable(e));
			throw new ConnectionFailedException(e);
		}
	}

	/**
	 * The method register a new application to be executed according to the class
	 * of the voice application
	 * 
	 * @param class
	 *            instance of the class that contains the voice application (extends
	 *            from callController)
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	public void registerVoiceApp(Class<? extends CallController> controllerClass) {
		callSupplier = new Supplier<CallController>() {

			@Override
			public CallController get() {
				try {
					return controllerClass.newInstance();

				} catch (InstantiationException | IllegalAccessException e) {
					lastException = e;
					return hangupDefault();
				}
			}
		};
	}

	/**
	 * The method register the voice application (the supplier that has a
	 * CallController, meaning the application)
	 * 
	 * @param controllorSupplier
	 *            the supplier that has the CallController (the voice application)
	 */
	public void registerVoiceApp(Supplier<CallController> controllorSupplier) {
		if (Objects.isNull(controllorSupplier))
			return;

		callSupplier = controllorSupplier;
	}

	/**
	 * The method register the voice application and execute it
	 * 
	 * @param cc
	 */
	public void registerVoiceApp(Consumer<CallController> cc) {
		callSupplier = () -> {
			return new CallController() {

				@Override
				public CompletableFuture<Void> run() {
					return CompletableFuture.runAsync(() -> {
						cc.accept(this);
					});
				}
			};
		};
	}

	/**
	 * The method hangs up the call if we can't create an instance of the class that
	 * contains the voice application
	 * 
	 * @param e
	 * @return
	 */

	protected CallController hangupDefault() {
		return new CallController() {

			public CompletableFuture<Void> run() {
				return hangup().run().thenAccept(hangup -> {
					if (Objects.isNull(lastException))
						logger.severe("Your Application is not registered!");
					logger.severe("Invalid application!");
				});
			}
		};
	}

	@Override
	public void onSuccess(Message event) {

		if (event instanceof StasisStart) {
			StasisStart ss = (StasisStart) event;

			if (Objects.equals(ss.getChannel().getDialplan().getExten(), "h")) {
				logger.info("ignore h");
				return;
			}
			logger.info("asterisk id: " + event.getAsterisk_id());
			// if the list contains the stasis start event with this channel id, remove it
			// and continue
			if (ignoredChannelIds.remove(ss.getChannel().getId())) {
				return;
			}
			logger.info("Channel id of the caller: " + ss.getChannel().getId());

			CallController cc = callSupplier.get();
			cc.init(ss, ari, this);
			try {
				cc.run().exceptionally(t -> {
					logger.severe("Completation error while running the application " + ErrorStream.fromThrowable(t));
					cc.hangup().run();
					return null;
				});
			} catch (Throwable t) {
				logger.severe("Error running the voice application: " + ErrorStream.fromThrowable(t));
				cc.hangup().run();
			}
			return;
		}

		/*String channelId = getEventChannelId(event);
		if (Objects.isNull(channelId))
			return;*/
		//setEventChannelId(channelId, event);

		// look for a future event in the event list
				Iterator<Function<Message, Boolean>> itr = futureEvents.iterator();

				while (itr.hasNext()) {
					Function<Message, Boolean> currEntry = itr.next();
					if (currEntry.apply(event)) {
						// remove from the list of future events
						itr.remove();
						logger.info("future event was removed "+event.toString());
						break;
					}
				}
	}

	/**
	 * get the channel id of the current event. if no channel id to this event, null
	 * is returned
	 * 
	 * @param event
	 *            event message that we are checking
	 * @return
	 */
	private String getEventChannelId(Message event) {
		Class<?> msgClass = event.getClass();

		Channel channel;
		if (event instanceof Dial_impl_ari_2_0_0) {
			channel = ((Dial_impl_ari_2_0_0) event).getPeer();
			return channel.getId();
		}

		try {
			Method method = msgClass.getDeclaredMethod("getChannel", null);
			Object res2 = method.invoke(event, null);
			if (Objects.nonNull(res2))
				return ((Channel_impl_ari_2_0_0) res2).getId();

		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			logger.fine(
					"Cannot get channel id for event type: " + event.getType() + ":" + ErrorStream.fromThrowable(e));
		}
		return null;
	}

	@Override
	public void onFailure(RestException e) {
		logger.warning(e.getMessage());
	}

	/**
	 * set the channel id of the event before removing it from future event list
	 * 
	 * @param channelId
	 *            channel id of the event
	 * @param event
	 *            message event
	 */
	/*public void setEventChannelId(String channelId, Message event) {
		for (SavedEventPair savedEventPair : futureEvents) {
			if (savedEventPair.getFunc().apply(event))
				savedEventPair.setChannelId(channelId);
		}
	}*/

	/**
	 * The method handles adding a future event from a specific class (event) and a
	 * channel id to the future event list
	 * 
	 * @param class1
	 *            class of the finished event (example: PlaybackFinished)
	 * @param func
	 *            function to be executed
	 */
	protected <T> void addFutureEvent(Class<T> class1, Function<T, Boolean> func) {

		@SuppressWarnings("unchecked")
		Function<Message, Boolean> futureEvent = (Message message) -> {

			if (class1.isInstance(message))
				return func.apply((T) message);
			return false;
		};

		futureEvents.add(futureEvent);
	}

	/**
	 * get the name of the application
	 * 
	 * @return
	 */
	public String getAppName() {
		return appName;
	}

	public Exception getLastException() {
		return lastException;
	}

	/**
	 * ignore Stasis start from this channel (package private method)
	 * 
	 * @param id
	 *            channel id to ignore
	 */
	void ignoreChannel(String id) {
		ignoredChannelIds.add(id);
	}

	/**
	 * disconnect from the websocket (user's choice if to call it or not)
	 */
	public void disconnect() {

		try {
			ari.closeAction(ari.events());
			logger.info("closing the web socket");
		} catch (ARIException e) {
			logger.info("failed closing the web socket");
		}

	}

	/**
	 * Get the url that we are connected to
	 * 
	 * @return
	 */
	public String getConnetion() {
		return ari.getUrl();
	}

	/**
	 * get call supplier
	 * 
	 * @return
	 */
	public Supplier<CallController> getCallSupplier() {
		return callSupplier;
	}

	/**
	 * set call supplier
	 * 
	 * @param callSupplier
	 */
	public void setCallSupplier(Supplier<CallController> callSupplier) {
		this.callSupplier = callSupplier;
	}

	public ARI getAri() {
		return ari;
	}

}

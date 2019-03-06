package io.cloudonix.arity;

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.BridgeCreated;
import ch.loway.oss.ari4java.generated.BridgeDestroyed;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.DeviceStateChanged;
import ch.loway.oss.ari4java.generated.Message;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.PlaybackStarted;
import ch.loway.oss.ari4java.generated.RecordingFinished;
import ch.loway.oss.ari4java.generated.RecordingStarted;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.Dial_impl_ari_2_0_0;
import ch.loway.oss.ari4java.tools.ARIException;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ConnectionFailedException;
import io.cloudonix.arity.errors.ErrorStream;

/**
 * The class represents the creation of ARI and websocket service that handles
 * the incoming events
 * 
 * @author naamag
 *
 */
public class ARIty implements AriCallback<Message> {
	private final static Logger logger = Logger.getLogger(ARIty.class.getName());
	private Queue<SavedEvent<?>> futureEvents = new ConcurrentLinkedQueue<SavedEvent<?>>();
	private ARI ari;
	private String appName;
	private Supplier<CallController> callSupplier = this::hangupDefault;
	// save the channel id of new calls (for ignoring another stasis start event, if
	// needed)
	private ConcurrentSkipListSet<String> ignoredChannelIds = new ConcurrentSkipListSet<>();
	private Exception lastException = null;
	private ExecutorService executor = ForkJoinPool.commonPool();
	private Consumer<Exception> ce;

	/**
	 * Constructor
	 * 
	 * @param uri     URI
	 * @param appName name of the stasis application
	 * @param login   user name
	 * @param pass    password
	 * 
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 */
	public ARIty(String uri, String appName, String login, String pass)
			throws ConnectionFailedException, URISyntaxException {
		this(uri, appName, login, pass, true, null);
	}

	/**
	 * Constructor
	 * 
	 * @param uri           URI
	 * @param appName       name of the stasis application
	 * @param login         user name
	 * @param pass          password
	 * @param openWebSocket if need to open web socket in order to process events
	 *                      true, false otherwise
	 * 
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 */
	public ARIty(String uri, String appName, String login, String pass, boolean openWebSocket)
			throws ConnectionFailedException, URISyntaxException {
		this(uri, appName, login, pass, openWebSocket, null);
	}

	/**
	 * Constructor
	 * 
	 * @param uri           URI
	 * @param appName       name of the stasis application
	 * @param login         user name
	 * @param pass          password
	 * @param openWebSocket if need to open web socket in order to process events
	 *                      true, false otherwise
	 * @param ce            error handler
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 */
	public ARIty(String uri, String appName, String login, String pass, boolean openWebSocket, Consumer<Exception> ce)
			throws ConnectionFailedException, URISyntaxException {
		this.appName = appName;
		this.ce = (Objects.isNull(ce)) ? e -> {
		} : ce;

		try {
			ari = ARI.build(uri, appName, login, pass, AriVersion.ARI_2_0_0);
			logger.info("Ari created");
			logger.info("Ari version: " + ari.getVersion());
			if (openWebSocket) {
				ari.events().eventWebsocket(appName, true, this);
				logger.info("Websocket is open");
			}
		} catch (ARIException e) {
			logger.severe("Connection failed: " + ErrorStream.fromThrowable(e));
			throw new ConnectionFailedException(e);
		}
	}

	/**
	 * The method register a new application to be executed according to the class
	 * of the voice application
	 * 
	 * @param class instance of the class that contains the voice application
	 *        (extends from callController)
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
	 * @param controllorSupplier the supplier that has the CallController (the voice
	 *                           application)
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
			executor.submit(() -> handleStasisStart(event));
			return;
		}
		executor.submit(() -> handleOtherEvents(event));
	}

	private void handleOtherEvents(Message event) {
		String channelId = getEventChannelId(event);
		if (Objects.isNull(channelId))
			return;

		logger.fine("Looking for event handler of " + event.getClass() + " for channel " + channelId);
		// look for a future event in the event list
		Iterator<SavedEvent<?>> itr = futureEvents.iterator();
		while (itr.hasNext()) {
			SavedEvent<?> currEntry = itr.next();
			if (!Objects.equals(currEntry.getChannelId(), channelId))
				continue;
			logger.fine("Matched channel ID for " + event.getClass() + " - " + channelId);
			currEntry.accept(event);
		}
	}

	private void handleStasisStart(Message event) {
		StasisStart ss = (StasisStart) event;
		if ("h".equals(ss.getChannel().getDialplan().getExten())) {
			logger.fine("Ignore h");
			return;
		}
		// if the list contains the stasis start event with this channel id, remove it
		// and continue
		if (ignoredChannelIds.remove(ss.getChannel().getId())) {
			return;
		}
		logger.info("asterisk id: " + event.getAsterisk_id() + " and channel id is: " + ss.getChannel().getId());
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

	/**
	 * get the channel id of the current event. if no channel id to this event, null
	 * is returned
	 * 
	 * @param event event message that we are checking
	 * @return
	 */
	private String getEventChannelId(Message event) {
		if (event instanceof DeviceStateChanged || event instanceof BridgeCreated || event instanceof BridgeDestroyed)
			return null; // skip this, it never has a channel

		if (event instanceof Dial_impl_ari_2_0_0)
			return ((Dial_impl_ari_2_0_0) event).getPeer().getId();

		if (event instanceof PlaybackStarted)
			return ((PlaybackStarted) event).getPlayback().getTarget_uri()
					.substring(((PlaybackStarted) event).getPlayback().getTarget_uri().indexOf(":") + 1);

		if (event instanceof PlaybackFinished)
			return ((PlaybackFinished) event).getPlayback().getTarget_uri()
					.substring(((PlaybackFinished) event).getPlayback().getTarget_uri().indexOf(":") + 1);

		if (event instanceof RecordingStarted)
			return ((RecordingStarted) event).getRecording().getTarget_uri()
					.substring(((RecordingStarted) event).getRecording().getTarget_uri().indexOf(":") + 1);
		if (event instanceof RecordingFinished)
			return ((RecordingFinished) event).getRecording().getTarget_uri()
					.substring(((RecordingFinished) event).getRecording().getTarget_uri().indexOf(":") + 1);

		try {
			Class<?> msgClass = event.getClass();
			Object chan = msgClass.getMethod("getChannel").invoke(event);
			if (Objects.nonNull(chan))
				return ((Channel) chan).getId();
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			logger.fine("Can not get channel id for event " + event + ": " + e);
		}
		return null;
	}

	@Override
	public void onFailure(RestException e) {
		logger.warning(e.getMessage());
		ce.accept(e);
	}

	/**
	 * The method handles adding a future event from a specific class with the
	 * channel id to the future event list
	 * 
	 * @param class1    class of the finished event (example: PlaybackFinished)
	 * @param channelId id of the channel we want to follow it's future event/s
	 * @param eventHandler      handler to call when the event arrives
	 */
	protected <T extends Message> SavedEvent<T> addFutureEvent(Class<T> class1, String channelId, BiConsumer<T,SavedEvent<T>> eventHandler) {
		logger.fine("Registering for " + class1 + " events on channel " + channelId);
		SavedEvent<T> se = new SavedEvent<T>(channelId, eventHandler, class1,this);
		futureEvents.add(se);
		return se;
	}

	/**
	 * The method handles adding a one time future event from a specific class with
	 * the channel id to the future event list
	 * 
	 * @param class1    class of the finished event (example: PlaybackFinished)
	 * @param channelId id of the channel we want to follow it's future event/s
	 * @param eventHandler      handler to call when the event arrives
	 */
	protected <T extends Message> void addFutureOneTimeEvent(Class<T> class1, String channelId, Consumer<T> eventHandler) {
		addFutureEvent(class1, channelId, (t,se) -> {
			se.unregister();
			eventHandler.accept(t);
		});
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
	 * @param id channel id to ignore
	 */
	void ignoreChannel(String id) {
		ignoredChannelIds.add(id);
	}

	/**
	 * disconnect from the websocket (user's choice if to call it or not)
	 */
	public void disconnect() {
		try {
			ari.cleanup();
		} catch (ARIException e) {
			logger.warning("Failed disconeccting: " + e);
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

	/**
	 * get ARI instance
	 * 
	 * @return
	 */
	public ARI getAri() {
		return ari;
	}

	/**
	 * remove saved event when no need to listen to it anymore
	 * 
	 * @param savedEvent the saved event to be removed
	 */
	public <T extends Message> void removeFutureEvent(SavedEvent<T>savedEvent) {
		if(futureEvents.remove(savedEvent))
			logger.info("Event "+savedEvent.getClass1().getName()+" was removed for channel: "+savedEvent.getChannelId());
		else
			logger.severe("Event "+savedEvent.getClass1().getName()+" was not removed for channel: "+savedEvent.getChannelId());
	}
	
	/**
	 * retry to execute ARI operation few times
	 * 
	 * @param op the ARI operation to execute
	 * @param retries number of retries to execute the operation
	 * 
	 * @return
	 */
	public <V>CompletableFuture<V> retryOperation(Consumer<AriCallback<V>> op, int retries){
		Timer timer = new Timer("Timer");
		AtomicReference<CompletableFuture<V>> future = new AtomicReference<>(new CompletableFuture<>());
		AtomicReference<Boolean> success = new AtomicReference<>(false);
		AtomicReference<Integer>tries = new AtomicReference<>(0);
		
		while(!success.get() && tries.get()<retries) {
			TimerTask task = new TimerTask() {
				@Override
				public void run() {
					tries.set(tries.get()+1);
					future.set(Operation.toFuture(op));
					future.get().thenAccept(v->success.set(true))
					.exceptionally(t->null);
				}
			};
			timer.schedule(task, TimeUnit.SECONDS.toMillis(1));
		}
		return future.get();
	}
}

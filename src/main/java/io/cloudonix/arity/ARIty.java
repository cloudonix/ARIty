package io.cloudonix.arity;

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.models.BridgeCreated;
import ch.loway.oss.ari4java.generated.models.BridgeDestroyed;
import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.DeviceStateChanged;
import ch.loway.oss.ari4java.generated.models.Message;
import ch.loway.oss.ari4java.generated.models.PlaybackFinished;
import ch.loway.oss.ari4java.generated.models.PlaybackStarted;
import ch.loway.oss.ari4java.generated.models.RecordingFinished;
import ch.loway.oss.ari4java.generated.models.RecordingStarted;
import ch.loway.oss.ari4java.generated.models.StasisEnd;
import ch.loway.oss.ari4java.generated.models.StasisStart;
import ch.loway.oss.ari4java.tools.ARIException;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ConnectionFailedException;
import io.cloudonix.arity.helpers.Lazy;
import io.cloudonix.arity.helpers.Timers;

/**
 * The class represents the creation of ARI and websocket service that handles
 * the incoming events
 *
 * @author naamag
 *
 */
public class ARIty implements AriCallback<Message> {
	private final static Logger logger = LoggerFactory.getLogger(ARIty.class);
	private Queue<EventHandler<?>> eventHandlers = new ConcurrentLinkedQueue<>();
	private Queue<EventHandler<?>> rawEventHandlers = new ConcurrentLinkedQueue<>();
	private ARI ari;
	private String appName;
	private Supplier<CallController> callSupplier = this::hangupDefault;
	private ConcurrentHashMap<String, Consumer<CallState>> stasisStartListeners = new ConcurrentHashMap<>();
	private Consumer<Exception> ce;
	private Lazy<Channels> channels = new Lazy<>(() -> new Channels(this));
	private Lazy<Bridges> bridges = new Lazy<>(() -> new Bridges(this));
	private ExecutorService threadpool = Executors.newCachedThreadPool();
	boolean autoBindBridges = false;
	private String url;

	/**
	 * Create and connect ARIty to Asterisk
	 *
	 * @param uri     Asterisk ARI URI
	 * @param appName name of the stasis application
	 * @param login   user name
	 * @param pass    password
	 *
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 */
	public ARIty(String uri, String appName, String login, String pass)
			throws ConnectionFailedException, URISyntaxException {
		this(uri, appName, login, pass, true, AriVersion.IM_FEELING_LUCKY, null);
	}

	/**
	 * Create and connect ARIty to Asterisk
	 *
	 * @param uri           Asterisk ARI
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
	 * Create and connect ARIty to Asterisk
	 *
	 * @param uri           Asterisk ARI
	 * @param appName       name of the stasis application
	 * @param login         user name
	 * @param pass          password
	 * @param openWebSocket if need to open web socket in order to process events
	 *                      true, false otherwise
	 * @param ce            Handler to report connection exceptions to (set to null to ignore)
	 *
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 */
	public ARIty(String uri, String appName, String login, String pass, boolean openWebSocket, Consumer<Exception> ce)
			throws ConnectionFailedException, URISyntaxException {
		this(uri, appName, login, pass, openWebSocket, AriVersion.IM_FEELING_LUCKY, ce);
	}

	/**
	 * Create and connect ARIty to Asterisk
	 *
	 * @param uri           Asterisk ARI
	 * @param appName       name of the stasis application
	 * @param login         user name
	 * @param pass          password
	 * @param openWebSocket if need to open web socket in order to process events
	 *                      true, false otherwise
	 * @param version       ARI version to enforce
	 * @param ce            Handler to report connection exceptions to (set to null to ignore)
	 *
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 */
	public ARIty(String uri, String appName, String login, String pass, boolean openWebSocket, AriVersion version, Consumer<Exception> ce)
			throws ConnectionFailedException, URISyntaxException {
		this.appName = appName;
		this.ce = (Objects.isNull(ce)) ? e -> {
		} : ce;
		if (uri == null)
			return; // users might want to not connect, start ARIty just for tests
		if (!uri.endsWith("/"))
			uri += "/";

		try {
			ari = ARI.build(this.url = uri, appName, login, pass, version);
			logger.info("Ari created {}", url);
			logger.info("Ari version: " + ari.getVersion());
			if (openWebSocket) {
				ari.events().eventWebsocket(appName).setSubscribeAll(true).execute(this);
				logger.info("Websocket is open");
			}
		} catch (ARIException e) {
			logger.error("Connection failed: ",e);
			throw new ConnectionFailedException(e);
		}
	}
	
	/**
	 * Use the specified executor service to provide threads where new calls will be started.
	 * If this method is not called, ARIty uses a cache thread pool from {@link Executors}.
	 * @param service an ExecutorService to manage call threads
	 * @return itself for fluent calls
	 */
	public ARIty setExecutorService(ExecutorService service) {
		threadpool = service;
		return this;
	}
	
	/**
	 * Sets the default behavior for call controllers' bridge binding (see @link {@link CallController#bindToBridge()}
	 * @param shouldAutoBind set to <code>true</code> to have all call controller automatically bind to a bridge on init
	 * (as needed). The default currently is not to auto-bind
	 * @return itself for fluend calls
	 */
	public ARIty setAutoBindBridges(boolean shouldAutoBind) {
		this.autoBindBridges = shouldAutoBind;
		return this;
	}
	
	/**
	 * Execute a task (such as completing a CompletableFuture) in the ARIty completion executor service 
	 * @param task task to dispatch using the executor
	 */
	void dispatchTask(Runnable task) {
		CompletableFuture.runAsync(task, threadpool);
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
					return controllerClass.getConstructor().newInstance();
				} catch (Throwable e) {
					logger.error("Failed to instantiate call controller from no-args c'tor of " + controllerClass
							+ ": ",e);
					return hangupDefault();
				}
			}
		};
	}

	/**
	 * The method register the voice application (the supplier that has a
	 * CallController, meaning the application)
	 *
	 * @param controllorSupplier the supplier that has the CallController (the voice application)
	 */
	public void registerVoiceApp(Supplier<CallController> controllorSupplier) {
		if (Objects.isNull(controllorSupplier))
			return;
		callSupplier = controllorSupplier;
	}

	/**
	 * Register a closure as the call application.
	 * 
	 * ARIty will call the provided function when a call is received and provide it a
	 * call controller for the incoming call.
	 *
	 * @param cc call controller handler to receive the call
	 */
	public void registerVoiceApp(Consumer<CallController> cc) {
		callSupplier = () -> {
			return new CallController() {

				@Override
				public CompletableFuture<Void> run() {
					return CompletableFuture.runAsync(() -> {
						cc.accept(this);
					}, threadpool).exceptionally(err -> {
						logger.error("Application " + cc + " failed with an error:", err);
						hangup().run();
						return null;
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
					logger.error("Your Application is not registered!");
				});
			}
		};
	}

	/**
	 * Initialize an existing call controller for an existing channel
	 *
	 * Use this to have ARIty set up the ARIty call state and call monitor for an existing channel. ARIty will retrieve the channel
	 * from ARI and then initialize the provided Call Controller instance, eventually calling {@link CallController#init()}.
	 *
	 * It is highly recommended to use a new call controller instance with this method and not an instance that has already been run.
	 *
	 * @param controller Call controller to set up
	 * @param channelId Asterisk channel ID to request from Asterisk
	 */
	public <T extends CallController> CompletableFuture<T> initFromChannel(T controller, String channelId) {
		return getCallState(channelId)
				.thenAccept(controller::init)
				.thenApply(v -> controller);
	}

	/**
	 * Generate a new call state for an existing channel.
	 *
	 * Useful for applications that create new channels and want to monitor them. Please note that the retrieved
	 * call state instance does not share any data with other {@link CallState} instances that monitor the same
	 * channel.
	 * @param channelId ID of channel to monitor
	 * @return A promise for a new call state instance for that channel
	 */
	public CompletableFuture<CallState> getCallState(String channelId) {
		return Operation.<Channel>retry(h -> ari.channels().get(channelId).execute(h))
				.thenApply(chan -> new CallState(chan, this));
	}

	@Override
	public void onSuccess(Message event) {
		if (event instanceof StasisStart) {
			threadpool.submit(() -> handleStasisStart(event));
			return;
		}

		String channelId = getEventChannelId(event);
		logger.debug("Received event " + event.getClass().getSimpleName() + " on channel " + channelId);
		if (channelId != null)
			handleChannelEvents(event, channelId);
		// dispatch global event handlers
		for (Iterator<EventHandler<?>> itr = rawEventHandlers.iterator(); itr.hasNext(); )
			itr.next().accept(event);
	}

	private void handleChannelEvents(Message event, String channelId) {
		for (Iterator<EventHandler<?>> itr = eventHandlers.iterator(); itr.hasNext(); ) {
			EventHandler<?> currEntry = itr.next();
			if (!Objects.equals(currEntry.getChannelId(), channelId))
				continue;
			currEntry.accept(event);
		}
		if (event instanceof StasisEnd) // clear event handlers for this channel
			eventHandlers.removeIf(e -> e.getChannelId().equals(((StasisEnd)event).getChannel().getId()));
	}

	private void handleStasisStart(Message event) {
		StasisStart ss = (StasisStart) event;
		if ("h".equals(ss.getChannel().getDialplan().getExten())) {
			logger.debug("Ignoring Stasis Start with 'h' extension, listen on channel hangup event if you want to handle hangups");
			return;
		}

		CallState callState = new CallState(ss, this);

		// see if an application waits for this channel
		Consumer<CallState> channelHandler = stasisStartListeners.remove(ss.getChannel().getId());
		if (Objects.nonNull(channelHandler)) {
			logger.debug("Sending stasis start for " + ss.getChannel().getId() + " to event handler " + channelHandler);
			channelHandler.accept(callState);
			return;
		}

		logger.debug("Stasis started with asterisk id: " + event.getAsterisk_id() + " and channel id is: " + ss.getChannel().getId());
		try {
			CallController cc = Objects.requireNonNull(callSupplier.get(),
					"User call controller supplier failed to provide a CallController to handle the call");
			cc.init(callState);
			(autoBindBridges ? cc.bindToBridge() : CompletableFuture.completedFuture(null)).thenComposeAsync(v -> cc.run(), threadpool).whenComplete((v,t) -> {
				if (Objects.nonNull(t)) {
					logger.error("Completation error while running the application ",t);
					channels().hangup(callState.getChannelId());
				}
			});
		} catch (Throwable t) { // a lot of user code is running here, so lets make sure they don't crash us
			logger.error("Unexpected error due to user code failure: ",t);
			channels().hangup(callState.getChannelId());
		}
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

		if (event instanceof ch.loway.oss.ari4java.generated.models.Dial)
			return ((ch.loway.oss.ari4java.generated.models.Dial) event).getPeer().getId();

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
			logger.warn("Channel ID is not set for event " + event);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			logger.warn("Can not get channel id for event " + event + ": " + e);
		}
		return null;
	}

	@Override
	public void onFailure(RestException e) {
		logger.warn(e.getMessage());
		ce.accept(e);
	}

	/**
	 * Register an event handler for a specific message on a specific channel
	 * @param type          type of message to listen to (example: PlaybackFinished)
	 * @param channelId     id of the channel to listen on
	 * @param eventHandler  handler to call when the event arrives
	 */
	public <T extends Message> EventHandler<T> addEventHandler(Class<T> type, String channelId, BiConsumer<T,EventHandler<T>> eventHandler) {
		logger.debug("Registering for {} events on channel {}", type.getSimpleName(), channelId);
		EventHandler<T> se = new EventHandler<T>(channelId, eventHandler, type, this);
		eventHandlers.add(se);
		return se;
	}
	
	/**
	 * Register an event handler for a specific message that may not be channel specific.
	 * This type of handler can also be used if you want to listen to the same events on multiple channels and filter the channels yourself
	 * @param type          type of message to listen to (example: PlaybackFinished)
	 * @param eventHandler  handler to call when the event arrives
	 */
	public <T extends Message> EventHandler<T> addGeneralEventHandler(Class<T> type, BiConsumer<T, EventHandler<T>> eventHandler) {
		logger.debug("Registering for {} global events", type.getSimpleName());
		EventHandler<T> se = new EventHandler<T>(null, eventHandler, type, this);
		rawEventHandlers.add(se);
		return se;
	}

	/**
	 * remove event handler when no need to listen to it anymore
	 * @param handler the event handler to be removed
	 */
	public <T extends Message> void removeEventHandler(EventHandler<T>handler) {
		if(eventHandlers.remove(handler))
			logger.debug("{} was removed", handler);
	}

	/**
	 * Register a one-off event handler for a specific message on a specific channel.
	 *
	 * After the event is triggered once, the event handler is automatically unregistererd.
	 * @param type          type of message to listen to (example: PlaybackFinished)
	 * @param channelId     id of the channel to listen on
	 * @param eventHandler  handler to call when the event arrives
	 * @return 
	 */
	public <T extends Message> EventHandler<T> listenForOneTimeEvent(Class<T> type, String channelId, Consumer<T> eventHandler) {
		return addEventHandler(type, channelId, (t,se) -> {
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

	/**
	 * Allow an ARIty application to take control of a known channel, before it enters ARI.
	 *
	 * This is useful with the ARIty application creates managed channels by itself
	 * @param id Known channel ID to wait for
	 * @param eventHandler handle that will receive the {@link CallState} object for the channel
	 * when it enters ARI
	 */
	public void registerApplicationStartHandler(String id, Consumer<CallState> eventHandler) {
		stasisStartListeners.put(id, eventHandler);
	}
	
	/**
	 * Convenience method for callers that want to wait for the channel to get into ARI stasis.
	 * The promise returned from this method is not guaranteed to resolve. If you think it is possible then channel
	 * will not enter stasis, you may want to use the {@link #registerApplicationStartHandler(String, Duration)} method
	 * instead, to get a timeout exception if the channel did not enter stasis after a set time.
	 * @param id channel ID to wait for
	 * @return a promise that will resolve when the channel enters stasis, with the new ARIty call state
	 */
	public CompletableFuture<CallState> registerApplicationStartHandler(String id) {
		CompletableFuture<CallState> promise = new CompletableFuture<>();
		registerApplicationStartHandler(id, promise::complete);
		return promise;
	}
	
	/**
	 * Convenience method for callers that want to wait for the channel to get into ARI stasis.
	 * @param id channel ID to wait for
	 * @param timeout amount of time to wait for the channel to enter stasis, after which the promise will be rejected
	 * with a {@link TimeoutException}
	 * @return a promise that will resolve when the channel enters stasis, with the new ARIty call state, or rejected
	 * with a {@link TimeoutException}
	 */
	public CompletableFuture<CallState> registerApplicationStartHandler(String id, Duration timeout) {
		CompletableFuture<CallState> promise = registerApplicationStartHandler(id);
		Timers.schedule(
				() -> promise.completeExceptionally(
						new TimeoutException("Channel " + id + " did not enter stasis before timeout expired")),
				timeout.toMillis());
		return promise;
	}

	/**
	 * disconnect from the websocket (user's choice if to call it or not)
	 */
	public void disconnect() {
		if (ari.isWsConnected());
			ari.cleanup();
	}

	/**
	 * Initiate an unsolicited dial
	 * @param callerId Caller ID to be published to the destination
	 * @param destination Asterisk endpoint to be dialed to (including technology and URL)
	 * @return a Dial operation to configure further and run
	 */
	public Dial dial(String callerId, String destination) {
		return new Dial(this, callerId, destination);
	}

	/**
	 * Retrieve the URL that ARIty is connected to.
	 * @return the Asterisk ARI URL
	 */
	public String getConnetion() {
		return url;
	}

	/**
	 * Retrieve the ari4java instance that ARIty uses
	 * @deprecated Please do not use this method as it isn't guaranteed that ari4java will continue
	 * to be the underlying infrastructure in the future
	 * @return ari4java instance
	 */
	public ARI getAri() {
		return ari;
	}

	/**
	 * get all active channels
	 *
	 * @return
	 */
	public CompletableFuture<List<Channel>> getActiveChannels(){
		return Operation.retry(cb -> ari.channels().list().execute(cb));
	}

	public Channels channels() {
		return channels.get();
	}
	
	public Bridges bridges() {
		return bridges.get();
	}
}

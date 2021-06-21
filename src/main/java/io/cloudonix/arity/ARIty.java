package io.cloudonix.arity;

import static java.util.concurrent.CompletableFuture.*;

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

import io.cloudonix.arity.errors.ConnectionFailedException;
import io.cloudonix.arity.helpers.Lazy;
import io.cloudonix.arity.helpers.Timers;
import io.cloudonix.arity.impl.ARIConnection;
import io.cloudonix.arity.impl.ARIConnection.Version;
import io.cloudonix.arity.impl.ari4java.ARI4JavaConnection;

/**
 * ARIty main API, connecting to an Asterisk server.
 * 
 * This class sends commands to an Asterisk REST Interface and (optionally) receives ARI events and
 * dispatches them to your application.
 *
 * @author odeda
 * @author naamag
 */
public class ARIty {
	private final static Logger logger = LoggerFactory.getLogger(ARIty.class);
	private ConcurrentHashMap<String,Queue<EventHandler<?>>> channelEventHandlers = new ConcurrentHashMap<>();
	private Queue<EventHandler<?>> rawEventHandlers = new ConcurrentLinkedQueue<>();
	private ARIConnection connection;
	private String url;
	private String appName;
	private Supplier<CallController> callSupplier = this::hangupDefault;
	private ConcurrentHashMap<String, Consumer<CallState>> stasisStartListeners = new ConcurrentHashMap<>();
	private Consumer<Exception> ce;
	private Lazy<Channels> channels = new Lazy<>(() -> new Channels(this));
	private Lazy<Bridges> bridges = new Lazy<>(() -> new Bridges(this));
	ExecutorService threadpool = Executors.newCachedThreadPool();
	boolean autoBindBridges = false;

	/**
	 * Create and connect ARIty to Asterisk
	 * 
	 * <strong>Deprecated</strong>: this workflow does not allow to detect when a connection has been successful
	 * and hides important connection errors, and will be removed in version 1.0.
	 * Instead, use simple constructor and {@link #connect(String, String)} or one of its overrides;
	 *
	 * @param uri     Asterisk ARI URI
	 * @param appName name of the stasis application
	 * @param login   user name
	 * @param pass    password
	 *
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 * @deprecated use {@link #ARIty(String, String)} then call {@link #connect(String, String)} or one of its overrides
	 */
	public ARIty(String uri, String appName, String login, String pass)
			throws ConnectionFailedException, URISyntaxException {
		this(uri, appName);
		connect(login, pass);
	}

	/**
	 * Create and connect ARIty to Asterisk
	 * 
	 * <strong>Deprecated</strong>: this workflow does not allow to detect when a connection has been successful
	 * and hides important connection errors, and will be removed in version 1.0.
	 * Instead, use simple constructor and {@link #connect(String, String)} or one of its overrides;
	 *
	 * @param uri           Asterisk ARI
	 * @param appName       name of the stasis application
	 * @param login         user name
	 * @param pass          password
	 * @param receiveEvents if need to open web socket in order to process events
	 *                      true, false otherwise
	 *
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 * @deprecated use {@link #ARIty(String, String)} then call {@link #connect(String, String)} or one of its overrides
	 */
	public ARIty(String uri, String appName, String login, String pass, boolean receiveEvents)
			throws ConnectionFailedException, URISyntaxException {
		this(uri, appName);
		connect(login, pass, receiveEvents, null);
	}

	/**
	 * Create and connect ARIty to Asterisk
	 *
	 * @param uri           Asterisk ARI
	 * @param appName       name of the stasis application
	 * @param login         user name
	 * @param pass          password
	 * @param receiveEvents if need to open web socket in order to process events
	 *                      true, false otherwise
	 * @param ce            Handler to report connection exceptions to (set to null to ignore)
	 *
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 * @deprecated use {@link #ARIty(String, String)} then call {@link #connect(String, String)} or one of its overrides
	 */
	public ARIty(String uri, String appName, String login, String pass, boolean receiveEvents, Consumer<Exception> ce)
			throws ConnectionFailedException, URISyntaxException {
		this(uri, appName);
		connect(login, pass, receiveEvents, Version.LATEST).exceptionally(e -> { 
			if (e instanceof Exception) ce.accept((Exception) e);
			else ce.accept(new RuntimeException(e));
			return null; 
		});
	}

	/**
	 * Create and connect ARIty to Asterisk
	 * 
	 * <strong>Deprecated</strong>: this workflow does not allow to detect when a connection has been successful
	 * and hides important connection errors, and will be removed in version 1.0.
	 * Instead, use simple constructor and {@link #connect(String, String)} or one of its overrides;
	 *
	 * @param uri           Asterisk ARI
	 * @param appName       name of the stasis application
	 * @param login         user name
	 * @param pass          password
	 * @param receiveEvents if need to open web socket in order to process events
	 *                      true, false otherwise
	 * @param version       ARI version to enforce
	 * @param ce            Handler to report connection exceptions to (set to null to ignore)
	 *
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 * @deprecated use {@link #ARIty(String, String)} then call {@link #connect(String, String)} or one of its overrides
	 */
	public ARIty(String uri, String appName, String login, String pass, boolean receiveEvents, Version version, Consumer<Exception> ce)
			throws ConnectionFailedException, URISyntaxException {
		this(uri, appName);
		connect(login, pass, receiveEvents, version).exceptionally(e -> { 
			if (e instanceof Exception) ce.accept((Exception) e);
			else ce.accept(new RuntimeException(e));
			return null; 
		});
	}
	
	/**
	 * Create a new ARIty instance that will use the specified Asterisk and application name
	 * @param uri ARI URL of Asterisk
	 * @param appName the application name to register with Asterisk
	 */
	public ARIty(String uri, String appName) {
		this.url = uri;
		this.appName = appName;
	}
	
	/**
	 * Connect to Asterisk server with the specified username and password (that should be configured in ari.conf)
	 * @param username user name from ari.conf
	 * @param password password from ari.conf
	 * @return A promise that will resolve with the connection has completed or reject if it failed
	 */
	public CompletableFuture<Void> connect(String username, String password) {
		return connect(username, password, true, Version.LATEST);
	}
	
	/**
	 * Connect to Asterisk server with the specified username and password (that should be configured in ari.conf)
	 * optionally disabling event dispatch
	 * @param username user name from ari.conf
	 * @param password password from ari.conf
	 * @param receiveEvents whether ARIty should dispatch events received from ARI
	 * @return A promise that will resolve with the connection has completed or reject if it failed
	 */
	public CompletableFuture<Void> connect(String username, String password, boolean receiveEvents) {
		return connect(username, password, receiveEvents, Version.LATEST);
	}
	
	/**
	 * Connect to Asterisk server with the specified username and password (that should be configured in ari.conf)
	 * optionally specifying a minimum ARI version to require. The connection will fail if the connected Asterisk
	 * server does not support at least the specified version.
	 * @param username user name from ari.conf
	 * @param password password from ari.conf
	 * @param version Minimum version to require from Asterisk
	 * @return A promise that will resolve with the connection has completed or reject if it failed
	 */
	public CompletableFuture<Void> connect(String username, String password, Version version) {
		return connect(username, password, true, version);
	}
	
	/**
	 * Connect to Asterisk server with the specified username and password (that should be configured in ari.conf)
	 * optionally disabling event dispatch and specifying a minimum ARI version to require - the connection will fail
	 * if the connected Asterisk server does not support at least the specified version.
	 * @param username user name from ari.conf
	 * @param password password from ari.conf
	 * @param receiveEvents whether ARIty should dispatch events received from ARI
	 * @param version Minimum version to require from Asterisk
	 * @return A promise that will resolve with the connection has completed or reject if it failed
	 */
	public CompletableFuture<Void> connect(String username, String password, boolean receiveEvents, Version version) {
		this.ce = (Objects.isNull(ce)) ? e -> { } : ce;
		if (url == null) return completedFuture(null); // users might want to not connect, start ARIty just for tests
		if (!url.endsWith("/")) url += "/";
		connection = new ARI4JavaConnection(this, url, appName, username, password, receiveEvents);
		connection.setExecutorService(threadpool);
		return connection.connect(version);
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
	 * @return a call controller that hangs up
	 */
	private CallController hangupDefault() {
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
		return connection.getCallState(channelId);
	}

	public void handleStasisStart(CallState stasisCallState) {
		// see if an application waits for this channel
		Consumer<CallState> channelHandler = stasisStartListeners.remove(stasisCallState.getChannelId());
		if (Objects.nonNull(channelHandler)) {
			logger.debug("Sending stasis start for {} to event handler {}", stasisCallState.getChannelId(), channelHandler);
			dispatchTask(() -> channelHandler.accept(stasisCallState));
			return;
		}

		logger.debug("Stasis started for channel {}", stasisCallState.getChannelId());
		try {
			CallController cc = Objects.requireNonNull(callSupplier.get(),
					"User call controller supplier failed to provide a CallController to handle the call");
			cc.init(stasisCallState);
			(autoBindBridges ? cc.bindToBridge() : CompletableFuture.completedFuture(null)).thenComposeAsync(v -> cc.run(), threadpool).whenComplete((v,t) -> {
				if (Objects.nonNull(t)) {
					logger.error("Completation error while running the application ",t);
					channels().hangup(stasisCallState.getChannelId());
				}
			});
		} catch (Throwable t) { // a lot of user code is running here, so lets make sure they don't crash us
			logger.error("Unexpected error due to user code failure: ",t);
			channels().hangup(stasisCallState.getChannelId());
		}
	}
	
	public void handleStasisEnd(String channelId) {
		channelEventHandlers.remove(channelId);
	}
	
	public void handleChannelEvent(String channelId) {
		if (channelId != null)
			channelEventHandlers.computeIfAbsent(channelId, id -> new ConcurrentLinkedQueue<>()).forEach(h -> {
				h.accept(event);
			});
		// also see if we have a global event handler that would like to get this channel event
		handleGlobalEvent();
	}
	
	public void handleGlobalEvent() {
		for (Iterator<EventHandler<?>> itr = rawEventHandlers.iterator(); itr.hasNext(); )
			itr.next().accept(event);
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
		channelEventHandlers.computeIfAbsent(channelId, id -> new ConcurrentLinkedQueue<>()).add(se);
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
		if (handler.getChannelId() == null) { // it is a raw event handler
			rawEventHandlers.remove(handler);
			return;
		}
		if (channelEventHandlers.computeIfAbsent(handler.getChannelId(), 
				id -> new ConcurrentLinkedQueue<>()).remove(handler))
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

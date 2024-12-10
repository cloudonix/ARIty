package io.cloudonix.arity;

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
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
import ch.loway.oss.ari4java.tools.WsClient;
import ch.loway.oss.ari4java.tools.http.NettyHttpClient;
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
	
	public class Builder {

		private String uri;
		private String appName;
		private String login;
		private String password;
		private boolean openWebSocket = true;
		private AriVersion ariVersion = AriVersion.IM_FEELING_LUCKY;
		private Consumer<Exception> errorHandler = e -> {};
		private int connectionAttempts = 0; // do not change
		private NettyHttpClient httpClient;

		public Builder setUri(String uri) {
			this.uri = uri;
			return this;
		}

		public Builder setAppName(String appName) {
			this.appName = appName;
			return this;
		}

		public Builder setLogin(String login) {
			this.login = login;
			return this;
		}

		public Builder setPassword(String password) {
			this.password = password;
			return this;
		}

		public Builder setOpenWebSocket(boolean open) {
			this.openWebSocket = open;
			return this;
		}

		public Builder setAriVersion(AriVersion version) {
			this.ariVersion = version;
			return this;
		}

		public Builder setErrorHandler(Consumer<Exception> handler) {
			this.errorHandler = handler;
			return this;
		}
		
		public Builder setMaxConnectionAttempts(int attempts) {
			this.connectionAttempts = attempts;
			return this;
		}

		public NettyHttpClient createHttpClient() {
			if (httpClient != null)
				return httpClient;
			httpClient = new NettyHttpClient();
			if (connectionAttempts != 0)
				httpClient.setMaxReconnectCount(connectionAttempts);
			return httpClient;
		}

		public WsClient createWsClient() {
			return createHttpClient();
		}
	}
	
	private class ChannelCleanup {
		private Instant schedule;
		private String channelId;
		
		public ChannelCleanup(String channelId) {
			this.channelId = channelId;
			schedule = Instant.now().plusSeconds(30);
		}
		
		private boolean checkCleanup() {
			if (Instant.now().isBefore(schedule))
				return false;
			channelEventHandlers.remove(channelId);
			return true;
		}
	}
	
	private final static Logger logger = LoggerFactory.getLogger(ARIty.class);
	private ConcurrentHashMap<String,Queue<EventHandler<?>>> channelEventHandlers = new ConcurrentHashMap<>();
	private Queue<EventHandler<?>> rawEventHandlers = new ConcurrentLinkedQueue<>();
	private ARI ari;
	private String appName;
	private Consumer<CallState> defaultCallHandler = this::hangupDefault;
	private ConcurrentHashMap<String, Consumer<CallState>> stasisStartListeners = new ConcurrentHashMap<>();
	private Deque<ChannelCleanup> scheduledCleanups = new ConcurrentLinkedDeque<>();
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
		this(b -> b.setUri(uri).setAppName(appName).setLogin(login).setPassword(pass).setOpenWebSocket(openWebSocket)
				.setAriVersion(version).setErrorHandler(ce));
	}
		
	public ARIty(Consumer<Builder> builder) throws ConnectionFailedException, URISyntaxException {
		var b = new Builder();
		builder.accept(b);
		this.appName = Objects.requireNonNull(b.appName, "Application name must be specified");
		this.ce = b.errorHandler;
		if (b.uri == null)
			return; // users might want to not connect, start ARIty just for tests
		if (!b.uri.endsWith("/"))
			b.uri += "/";

		try {
			ari = ARI.build(this.url = b.uri, appName, b.login, b.password, b.ariVersion);
			ari.setHttpClient(b.createHttpClient());
			ari.setWsClient(b.createWsClient());
			logger.info("Ari created {}", url);
			logger.info("Ari version: " + ari.getVersion());
			if (b.openWebSocket) {
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
	 * @return itself for fluent calls
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
	
	private void getControllerAndRunCall(CallState newcall, Supplier<CallController> controllerSupplier) {
		try {
			initAndRun(controllerSupplier.get(), newcall);
		} catch (Throwable t) {
			logger.error("Failed to create new call controller to handle {}", newcall, t);
			channels().hangup(newcall.getChannelId());
		}
	}

	/**
	 * Register to receive all new stasis calls using a Call Controller supplier.
	 * 
	 * ARIty will call your supplier to generate a new {@link CallController} implementation for
	 * each new stasis call, after which it will call {@link CallController#init()} to initialize the
	 * call controller state and then the {@link CallController#run()} method to hand control over
	 * to your application.
	 *
	 * @param controllerSupplier a supplier that can generate ready to use {@link CallController} implementations
	 */
	public void registerVoiceApp(Supplier<CallController> controllerSupplier) {
		Objects.requireNonNull(controllerSupplier, "controllerSupplied is required");
		defaultCallHandler = cs -> getControllerAndRunCall(cs, controllerSupplier);
	}

	/**
	 * Register to receive all new stasis calls using the specified Call Controller implementation.
	 * 
	 * ARIty will use the default constructor on the specified class to create an instance
	 * for each new stasis call, calling {@link CallController#init()} to initialize the
	 * call controller state and then the {@link CallController#run()} method to hand control over
	 * to your application.
	 *
	 * @param controllerClass a controller class implementation that will handle default stasis calls
	 * @throws NoSuchMethodException if the provided class does not have a default constructor
	 */
	public void registerVoiceApp(Class<? extends CallController> controllerClass) throws NoSuchMethodException {
		var ctor = controllerClass.getConstructor();
		registerVoiceApp(() -> {
			try {
				return ctor.newInstance();
			} catch (Throwable e) {
				throw new RuntimeException("Failed to instantiate call controller "+controllerClass+" with default constructor", e);
			}
		});
	}

	/**
	 * Register to receive all new stasis calls as bare {@link CallController} instances.
	 * 
	 * ARIty will create a standard {@link CallController} instance for each new stasis call and
	 * pass it to the provided consumer to handle the call.
	 *
	 * @param callHandler a method that can accept a call controller instance.
	 */
	public void registerVoiceApp(Consumer<CallController> callHandler) {
		registerVoiceApp(() -> {
			return new CallController() {
				@Override
				public CompletableFuture<Void> run() {
					return CompletableFuture.runAsync(() -> {
						callHandler.accept(this);
					}, threadpool).exceptionally(err -> {
						logger.error("Application " + callHandler + " failed with an error:", err);
						hangup().run();
						return null;
					});
				}
			};
		});
	}

	/**
	 * Register to receive a specific stasis calls using a Call Controller supplier.
	 * 
	 * ARIty will call your supplier to generate a new {@link CallController} implementation for
	 * each new stasis call, after which it will call {@link CallController#init()} to initialize the
	 * call controller state and then the {@link CallController#run()} method to hand control over
	 * to your application.
	 *
	 * @param channelId the channel id of a known (or expected) channel for which Stasis has not started yet
	 * @param controllerSupplier a supplier that can generate ready to use {@link CallController} implementations
	 */
	public void registerVoiceApp(String channelId, Supplier<CallController> controllerSupplier) {
		waitForNewCallState(channelId).thenAccept(cs -> getControllerAndRunCall(cs, controllerSupplier));
	}

	/**
	 * Register to receive a specific stasis call using the specified Call Controller implementation.
	 * 
	 * ARIty will use the default constructor on the specified class to create an instance
	 * to handler the new stasis call, when it starts, calling {@link CallController#init()} to initialize the
	 * call controller state and then the {@link CallController#run()} method to hand control over
	 * to your application.
	 *
	 * @param channelId the channel id of a known (or expected) channel for which Stasis has not started yet
	 * @param controllerClass a controller class implementation that will handle default stasis calls
	 * @throws NoSuchMethodException if the provided class does not have a default constructor
	 */
	public void registerVoiceApp(String channelId, Class<? extends CallController> controllerClass) throws NoSuchMethodException {
		var ctor = controllerClass.getConstructor();
		registerVoiceApp(channelId, () -> {
			try {
				return ctor.newInstance();
			} catch (Throwable e) {
				throw new RuntimeException("Failed to instantiate call controller "+controllerClass+" with default constructor", e);
			}
		});
	}

	/**
	 * Register to receive a specific stasis call as bare {@link CallController} instance.
	 * 
	 * ARIty will create a standard {@link CallController} instance for each new stasis call and
	 * pass it to the provided consumer to handle the call.
	 *
	 * @param channelId the channel id of a known (or expected) channel for which Stasis has not started yet
	 * @param callHandler a method that can accept a call controller instance.
	 */
	public void registerVoiceApp(String channelId, Consumer<CallController> callHandler) {
		registerVoiceApp(channelId, () -> {
			return new CallController() {
				@Override
				public CompletableFuture<Void> run() {
					return CompletableFuture.runAsync(() -> {
						callHandler.accept(this);
					}, threadpool).exceptionally(err -> {
						logger.error("Application " + callHandler + " failed with an error:", err);
						hangup().run();
						return null;
					});
				}
			};
		});
	}
	
	/**
	 * Register to receive a specific stasis call as the raw call state, skipping initialization of a call controller.
	 * 
	 * When the application captures a new call in this way, ARIty will not perform any setup or cleanup of
	 * the captured channel, so auto-binding ({@link #setAutoBindBridges(boolean)}) and hanging up in case of errors is
	 * left for the application to implement.
	 * 
	 * @param channelId the channel id of a known (or expected) channel for which Stasis has not started yet
	 * @return a promise that will resolve when the channel enters stasis, with the new ARIty call state
	 */
	public CompletableFuture<CallState> waitForNewCallState(String channelId) {
		return waitForNewCallState(channelId, null);
	}
	
	/**
	 * Register to receive a specific stasis call as the raw call state, skipping initialization of a call controller.
	 * 
	 * When the application captures a new call in this way, ARIty will not perform any setup or cleanup of
	 * the captured channel, so auto-binding ({@link #setAutoBindBridges(boolean)}) and hanging up in case of errors is
	 * left for the application to implement.
	 * 
	 * @param channelId the channel id of a known (or expected) channel for which Stasis has not started yet
	 * @param timeout amount of time to wait for the channel to enter stasis, after which the promise will be rejected
	 *   with a {@link TimeoutException}
	 * @return a promise that will resolve when the channel enters stasis, with the new ARIty call state,
	 *   or reject with a {@link TimeoutException}
	 */
	public CompletableFuture<CallState> waitForNewCallState(String channelId, Duration timeout) {
		CompletableFuture<CallState> promise = new CompletableFuture<>();
		stasisStartListeners.put(channelId, promise::complete);
		if (timeout != null)
			Timers.schedule(() -> {
				if (stasisStartListeners.remove(channelId) != null)
					promise.completeExceptionally(new TimeoutException("Channel " + channelId + 
							" did not enter stasis before timeout expired"));
			}, timeout.toMillis());
		return promise;
	}

	/**
	 * Register to receive a specific stasis call as a default call controller implementation.
	 * 
	 * When the application captures a new call in this way, ARIty will not perform any setup or cleanup of
	 * the captured channel, so auto-binding ({@link #setAutoBindBridges(boolean)}) and hanging up in case of errors is
	 * left for the application to implement.
	 * 
	 * @param channelId the channel id of a known (or expected) channel for which Stasis has not started yet
	 * @return a promise that will resolve when the channel enters stasis, with a default {@link CallController}
	 */
	public CompletableFuture<CallController> waitForNewCall(String channelId) {
		return waitForNewCall(channelId, null);
	}

	/**
	 * Register to receive a specific stasis call as a default call controller implementation with no {@link CallController#run()}
	 * implementation, and that has not been run.
	 * 
	 * Calling {@link CallController#run()} on the received controller will return a rejected promise.
	 * 
	 * When the application captures a new call in this way, ARIty will not perform any setup or cleanup of
	 * the captured channel, so auto-binding ({@link #setAutoBindBridges(boolean)}) and hanging up in case of errors is
	 * left for the application to implement.
	 * 
	 * @param channelId the channel id of a known (or expected) channel for which Stasis has not started yet
	 * @param timeout amount of time to wait for the channel to enter stasis, after which the promise will be rejected
	 *   with a {@link TimeoutException}
	 * @return a promise that will resolve when the channel enters stasis, with a default {@link CallController}
	 */
	public CompletableFuture<CallController> waitForNewCall(String channelId, Duration timeout) {
		return waitForNewCallState(channelId, timeout).thenApply(cs -> {
			var cc = new CallController() {
				@Override
				public CompletableFuture<Void> run() {
					return CompletableFuture.failedFuture(new UnsupportedOperationException());
				}
			};
			cc.init(cs);
			return cc;
		});
	}

	/**
	 * Internal implementation for hanging up the call immediately. This is the default
	 * handler for new stasis call if no call handler is registered, or the default
	 * call handler failed to start
	 * @param newCall the new call state
	 */
	protected void hangupDefault(CallState newCall) {
		logger.error("No application is registered to handle the call!");
		channels().hangup(newCall.getChannelId());
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
		logger.debug("Received event {} {}", event.getClass().getSimpleName(), channelId == null ? "" : (
				"on channel " + channelId));
		if (channelId != null)
			handleChannelEvents(event, channelId);
		// dispatch global event handlers
		for (Iterator<EventHandler<?>> itr = rawEventHandlers.iterator(); itr.hasNext(); )
			itr.next().accept(event);
		var c = scheduledCleanups.poll();
		if (c != null && !c.checkCleanup())
			scheduledCleanups.offerFirst(c);
	}

	private void handleChannelEvents(Message event, String channelId) {
		// fire events in reverse addition order: newer listeners get to handle the event first.
		// The event "bubbles" from deeper elements to upper elements 
		new ArrayDeque<>(channelEventHandlers.computeIfAbsent(channelId, id -> new ConcurrentLinkedQueue<>()))
		.descendingIterator().forEachRemaining(h -> h.accept(event));
		// clear event handlers for this channel on stasis end
		if (event instanceof StasisEnd)
			scheduledCleanups.offerLast(new ChannelCleanup(channelId));
	}

	private void handleStasisStart(Message event) {
		StasisStart ss = (StasisStart) event;
		var channel = ss.getChannel();
		if ("h".equals(channel.getDialplan().getExten())) {
			logger.debug("Ignoring Stasis Start with 'h' extension, listen on channel hangup event if you want to handle hangups");
			return;
		}

		CallState callState = new CallState(ss, this);

		// see if an application waits for this channel
		Consumer<CallState> channelHandler = stasisStartListeners.remove(channel.getId());
		if (channelHandler != null) {
			logger.debug("Stasis started for {} (id: {}), handling using {}", channel.getId(), event.getAsterisk_id(), channelHandler);
			threadpool.execute(() -> channelHandler.accept(callState));
			return;
		}

		logger.debug("Stasis started for {} (id: {}), running default handler", ss.getChannel().getId(), event.getAsterisk_id());
		defaultCallHandler.accept(callState);
	}
	
	public void initAndRun(CallController controller, CallState callState) {
		logger.debug("Initializing and running call controller {}", controller);
		try {
			Objects.requireNonNull(controller, "Missing call controller to handle the call").init(callState);
			(autoBindBridges ? controller.bindToBridge() : CompletableFuture.completedFuture(null))
			.thenComposeAsync(v -> controller.run(), threadpool)
			.whenComplete((v,t) -> {
				if (t != null) {
					logger.error("Completation error while running the application ",t);
					channels().hangup(callState.getChannelId());
				}
			});
		} catch (Throwable t) { // a lot of user code is running here, so lets make sure they don't crash us
			logger.error("Unexpected error due to user code failure: ",t);
			channels().hangup(callState.getChannelId());
			throw t;
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
		EventHandler<T> se = new EventHandler<T>(channelId, eventHandler, type, this);
		logger.debug("Registering {}", se);
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
		EventHandler<T> se = new EventHandler<T>(null, eventHandler, type, this);
		logger.debug("Registering {}", se);
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
			logger.debug("Removed {}", handler);
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
		EventHandler<T> se = new OnetimeEventHandler<T>(channelId, eventHandler, type, this);
		logger.debug("Registering {}", se);
		channelEventHandlers.computeIfAbsent(channelId, id -> new ConcurrentLinkedQueue<>()).add(se);
		return se;
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

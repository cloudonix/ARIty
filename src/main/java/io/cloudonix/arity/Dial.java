package io.cloudonix.arity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ChannelStateChange;
import io.cloudonix.arity.errors.ErrorStream;
import io.cloudonix.arity.errors.dial.ChannelNotFoundException;
import io.cloudonix.lib.Futures;

/**
 * The class represents the Dial operation
 * 
 * @author naamag
 *
 */
public class Dial extends CancelableOperations {
	
	public enum Status {
		UNKNOWN("UNKNOWN"), 
		CHANUNAVAIL("CHANUNAVAIL"),
		CONGESTION("CONGESTION"),
		PROGRESS("PROGRESS"),
		NOANSWER("NOANSWER"),
		BUSY("BUSY"), 
		RINGING("RINGING"),
		ANSWER("ANSWER"), 
		CANCEL("CANCEL"),
		DONTCALL("DONTCALL"),
		TORTURE("TORTURE"),
		INVALIDARGS("INVALIDARGS");
		
		private String name;

		private Status(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
	}

	private CompletableFuture<Dial> compFuture = new CompletableFuture<>();
	private String endpoint;
	private String endpointChannelId = UUID.randomUUID().toString();
	private Instant dialStartTime;
	private Instant answerTime;
	private Instant ringingTime;
	private Instant endTime;
	private Duration callDuration, ringingDuration, mediaDuration;
	private final static Logger logger = Logger.getLogger(Dial.class.getName());
	private transient Status dialStatus = Status.UNKNOWN;
	private Map<String, String> headers = new Hashtable<>();
	private Map<String, String> variables = new Hashtable<>();
	private String callerId;
	private int timeout;
	private List<Runnable> channelStateUp = new ArrayList<>(),
			channelStateRinging = new ArrayList<>(),
			channelStateFail = new ArrayList<>(),
			channelStateCancelled = new ArrayList<>(),
			channelStateDisconnected = new ArrayList<>();
	private transient boolean wasRinging = false,
			wasConnected = false,
			wasCancelled = false,
			wasDisconnected = false,
			wasFailed = false;
	private EventHandler<ChannelStateChange> channelStateChangedSe;
	private Channel channel;
	// for local channels, which by default we don't do
	private String otherChannelId = null;
	private String extension = null;
	private String context = null;
	private Long priority = null;
	private Bridge earlyBridge;
	private AtomicReference<CallState> dialledCallState = new AtomicReference<>();

	/**
	 * Dial from a call controller
	 * @param callController controller of the handled incoming call that triggers the dial
	 * @param callerId Caller ID to be published to the destination
	 * @param destination Asterisk endpoint to be dialed to (including technology and URL)
	 */
	public Dial(CallController callController, String callerId, String destination) {
		this(callController.getChannelId(), callController.getARIty(), callerId, destination);
	}
	
	/**
	 * Initiate an unsolicited dial
	 * @param arity ARIty instance to run the operation against
	 * @param callerId
	 * @param destination
	 */
	Dial(ARIty arity, String callerId, String destination) {
		this(null, arity, callerId, destination);
	}

	/**
	 * Internal constructor
	 */
	private Dial(String originatingChannelId, ARIty arity, String callerId, String destination) {
		super(originatingChannelId, arity);
		this.endpoint = destination;
		this.callerId = callerId;
	}

	/**
	 * Add SIP headers to set on the outgoing SIP channel (only make sense if the destination endpoint
	 * uses "SIP" technology).
	 * @param headers list of SIP headers to set
	 * @return itself for fluent calls
	 */
	public Dial withHeaders(Map<String, String> headers) {
		this.headers.putAll(headers);
		return this;
	}
	
	/**
	 * Add a SIP header to set on the outgoing SIP channel (only makes sense if the destination endpoint
	 * uses "SIP" technology) 
	 * @param name header name
	 * @param value header value
	 * @return itself for fluent calls
	 */
	public Dial withHeader(String name, String value) {
		this.headers.put(name, value);
		return this;
	}
	
	/**
	 * Set Asterisk channel variables on the outgoing channel
	 * @param variables list of variables to set
	 * @return itself for fluent calls
	 */
	public Dial withVariables(Map<String, String> variables) {
		this.variables.putAll(variables);
		return this;
	}
	
	/**
	 * Add an Asterisk variable to set on the outgoing channel
	 * @param name variable name
	 * @param value variable value
	 * @return itself for fluent calls
	 */
	public Dial withVariable(String name, String value) {
		this.variables.put(name, value);
		return this;
	}
		
	/**
	 * Set the dial timeout (how long before we give up on waiting for answer
	 * @param timeout timeout in seconds
	 * @return itself for fluent calls
	 */
	public Dial withTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}
	
	/**
	 * Override the originator channel ID for the channel to be created by Dial.
	 * 
	 * If this Dial was created with a {@link CallController}, then the originator channel ID is
	 * already set from the <tt>CallController</tt> channel ID, but this call may be used to unset it by
	 * passing <tt>null</tt> as the value.
	 * @param channelId Originator channel id
	 * @return itself for fluent calls
	 */
	public Dial withOriginator(String channelId) {
		setChannelId(channelId);
		return this;
	}

	/**
	 * Connect the outbound channel to a local channel that will start in the specified dial plan.
	 * 
	 * Dial plan values (context, extension and priority) can be set to null (or 0) for starting the
	 * local channel in the default context and the null extension (which is only going to match catch alls).
	 * 
	 * @param context Context to run the local channel in
	 * @param extension Extension to dial in the dial plan context
	 * @param priority Priority to start in the context, set to 0 if you're not sure
	 * @return itself for fluent calls
	 */
	public Dial withLocalChannel(String context, String extension, long priority) {
		otherChannelId = UUID.randomUUID().toString();
		this.context = context;
		this.extension = extension;
		this.priority = priority;
		return this;
	}

	/**
	 * Connect the outbound channel to a dial plan context without a local channel.
	 * 
	 * Dial plan values (context, extension and priority) can be set to null (or 0) for starting the
	 * local channel in the default context and the null extension (which is only going to match catch alls).
	 * 
	 * @param context Context to run the local channel in
	 * @param extension Extension to dial in the dial plan context
	 * @param priority Priority to start in the context, set to 0 if you're not sure
	 * @return itself for fluent calls
	 */
	public Dial withDialPlan(String context, String extension, long priority) {
		this.context = context;
		this.extension = extension;
		this.priority = priority;
		return this;
	}
	
	/**
	 * Have this dial implement "early bridging" dial workflow for Asterisk 14, as suggested in
	 * https://blogs.asterisk.org/2016/08/24/asterisk-14-ari-create-bridge-dial/
	 * When a <tt>Dial</tt> with "early bridging" is {@link #run()}, it assumes the bridge was already
	 * created and the calling channel (if exists) has already been put into it. It will then create
	 * the outgoing channel without dialing it, set up the variables and headers, then dial the outgoing
	 * channel.
	 * @param bridge already created bridge to connect the outgoing channel to
	 * @return itself for fluent calls
	 */
	public Dial withBridge(Bridge bridge) {
		this.earlyBridge = bridge;
		return this;
	}
	
	/**
	 * The method dials to a number (end point)
	 * 
	 * @return A promise to return this instance when the dial operation completes
	 */
	public CompletableFuture<Dial> run() {
		logger.fine("Running Dial");
		getArity().registerApplicationStartHandler(endpointChannelId, dialledCallState::set);
		if (Objects.nonNull(getChannelId()))
			getArity().listenForOneTimeEvent(ChannelHangupRequest.class, getChannelId(), this::handleHangupCaller);
		getArity().listenForOneTimeEvent(ChannelHangupRequest.class, endpointChannelId, this::handleHangupCallee);
		channelStateChangedSe = getArity().addEventHandler(ChannelStateChange.class, endpointChannelId, this::handleChannelStateChanged);
		getArity().addEventHandler(ch.loway.oss.ari4java.generated.Dial.class, endpointChannelId, this::handleDialEvent);

		if (Objects.nonNull(earlyBridge))
			return runEarlyBridingWorkflow();
		
		return Operation.<Channel>retryOperation(
				cf -> channels().originate(endpoint, extension, context, priority, null, getArity().getAppName(), "",
						callerId, timeout, formatSIPHeaders(), endpointChannelId, otherChannelId, getChannelId(), null, cf))
				.thenAccept(channel -> {
					this.channel =  channel;
					logger.info("Dial started");
					dialStartTime = Instant.now();
				}).thenCompose(v -> compFuture);
	}

	private CompletableFuture<Dial> runEarlyBridingWorkflow() {
		if (Objects.nonNull(callerId)) {
			variables.putIfAbsent("CALLERID(num)", callerId);
			variables.putIfAbsent("CALLERID(name)", callerId);
		}
		logger.finer("Starting early bridging dial " + callerId + " -> " + endpoint);
		return Operation.<Channel>retryOperation(h -> channels().create(endpoint, getArity().getAppName(), "", endpointChannelId, 
				null, getChannelId(), null, h))
				.thenApply(ch -> channel = ch)
				.thenCompose(Futures.delay(50)) // TODO: replace with arity.waitForStasisStart(getChannelId())
				.whenComplete((v,t) -> logger.finer("Early bridging adding channel " + channel.getId() + " to bridge " + earlyBridge.getId()))
				.thenCompose(v -> earlyBridge.addChannel(channel.getId()))
				.whenComplete((v,t) -> logger.finer("Early bridging created channel " + channel.getId() + " setting variables and headers"))
				.thenCompose(v -> variables.entrySet().stream().map(this::setVariable).collect(Futures.resolvingCollector()))
				.thenApply(v -> formatSIPHeaders())
				.thenCompose(headers -> headers.entrySet().stream().map(this::setVariable).collect(Futures.resolvingCollector()))
				.whenComplete((v,t) -> logger.finer("Early bridging dialing out on " + endpointChannelId))
				.thenCompose(v -> Operation.<Void>retryOperation(h -> channels().dial(endpointChannelId, getChannelId(), timeout, h)))
				.thenRun(() -> {
					dialStartTime = Instant.now();
					logger.fine("Early bridged dial started " + callerId + " -> " + endpoint);
				})
				.thenCompose(v -> compFuture);
	}
	
	/**
	 * handle Dial event
	 * 
	 * @param dial dial event
	 * @return
	 */
	private void handleDialEvent(ch.loway.oss.ari4java.generated.Dial dial, EventHandler<ch.loway.oss.ari4java.generated.Dial>se) {
		logger.finer("Dial event detected on channel " + getChannelId() + ": " + dial.getDialstring() + " " + dial.getDialstatus());
		if (dialStatus == Status.CANCEL) {
			logger.info("Dial was canceled for channel id: " + dial.getPeer().getId());
			cancelled();
			se.unregister();
			return;
		}
		try {
			String status = dial.getDialstatus();
			if (!status.isEmpty()) // ignore empty status which can happen in response to originate (and means unknown)
				dialStatus = Status.valueOf(status);
		} catch (IllegalArgumentException e) {
			logger.severe("Unknown dial status " + dial.getDialstatus() + ", ignoring for now");
			dialStatus = Status.UNKNOWN;
		}
		logger.info("Dial status of channel with id: " + dial.getPeer().getId() + " is: " + dialStatus);
		switch (dialStatus) {
		case ANSWER:
			logger.info("Channel with id: " + dial.getPeer().getId() + " answered the call");
			connected();
			se.unregister();
			return;
		case BUSY:
		case NOANSWER:
		case CANCEL:
		case CHANUNAVAIL:
		case CONGESTION:
		case DONTCALL:
		case INVALIDARGS:
		case TORTURE:
			logger.info("The callee with channel id: "+ dial.getPeer().getId()+" can not answer the call, hanging up the call");
			Operation.<Void>retryOperation(cb -> channels().hangup(endpointChannelId, "normal", cb));
			failed();
			se.unregister();
			return;
		case PROGRESS:
		case RINGING:
			ringing();
			return;
		case UNKNOWN:
		}
	}
	
	private CompletableFuture<Void> setVariable(Map.Entry<String, String> var) {
		return Operation.retryOperation(h -> channels().setChannelVar(endpointChannelId, var.getKey(), var.getValue(), h));
	}

	/**
	 * Convert headers to ADDSIPHEADER variables as per
	 * http://www.xdev.net/2015/10/16/ari-originate-and-sip-headers/
	 * @return list variables representing SIP headers
	 */
	private Map<String, String> formatSIPHeaders() {
		Hashtable<String, String> out = new Hashtable<>();
		AtomicInteger i = new AtomicInteger(0);
		for (Map.Entry<String, String> header : headers.entrySet()) {
			out.put("SIPADDHEADER" + i.getAndIncrement(), header.getKey() + ":" + header.getValue());
		}
		return out;
	}

	/**
	 * handle hang up event of the caller
	 * 
	 * @param hangup
	 *            ChannelHangupRequest event
	 * @return
	 */
	private void handleHangupCaller(ChannelHangupRequest hangup) {
		cancel();
		logger.info("Caller hanged up the call");
	}

	/**
	 * handle hang up event of the callee
	 * 
	 * @param hangup
	 *            ChannelHangupRequest event
	 * @return
	 */
	private void handleHangupCallee(ChannelHangupRequest hangup) {
		logger.info("The called endpoint hanged up the call");
		channelStateChangedSe.unregister();
		disconnected();
	}

	/**
	 * @return
	 */
	@Override
	public CompletableFuture<Void> cancel() {
		logger.info("Hang up channel with id: " + endpointChannelId);
		dialStatus = wasConnected ? Status.ANSWER : Status.CANCEL;
		cancelled();
		return Operation.<Void>retryOperation(cb -> channels().hangup(endpointChannelId, "normal", cb))
				.thenAccept(v -> logger.info("Hang up the endpoint call"))
				.handle(this::mapExceptions);
	}

	/**
	 * get the number we are calling to
	 * 
	 * @return
	 */
	public String getEndPointNumber() {
		return endpoint;
	}

	/**
	 * set the number we are calling to
	 * 
	 * @param endPointNumber
	 */
	public void setEndPointNumber(String endPointNumber) {
		this.endpoint = endPointNumber;
	}

	/**
	 * get dial status of the call
	 * 
	 * @return
	 */
	public Status getDialStatus() {
		return dialStatus;
	}

	/**
	 * notice when channel state is Ringing
	 * @param func callback handler
	 * @return itself for chaining
	 */
	public Dial whenRinging(Runnable func) {
		channelStateRinging.add(func);
		return this;
	}
	
	private void ringing() {
		if(wasRinging)
			return;
		ringingTime = Instant.now();
		wasRinging = true;
		try {
			channelStateRinging.forEach(Runnable::run);
		} catch (Throwable t) {
			logger.severe("Fatal error running whenRinging callback: " +ErrorStream.fromThrowable(t));
		} 
	}

	/**
	 * register handler for handling when channel state is Up
	 * 
	 * @param func callback handler
	 * @return itself for chaining
	 */
	public Dial whenConnect(Runnable func) {
		channelStateUp.add(func);
		return this;
	}
	
	/**
	 * handle when the call was answered
	 */
	private void connected() {
		if (wasConnected)
			return;
		ringing();
		wasConnected = true;
		answerTime = Instant.now();
		try {
			channelStateUp.forEach(Runnable::run);
		} catch (Throwable t) {
			logger.severe("Fatal error running whenConnect callback: " +ErrorStream.fromThrowable(t));
		} 
	}

	/**
	 * Register handler for receiving a terminal failure status if the dial failed
	 * @param func callback handler
	 * @return itself for chaining
	 */
	public Dial whenFailed(Runnable func) {
		channelStateFail.add(func);
		return this;
	}
	
	/**
	 * handle when fail to dial
	 */
	private void failed() {
		if (wasFailed)
			return;
		wasFailed = true;
		computeDurationsAtEndOfCall();
		try {
			channelStateFail.forEach(Runnable::run);
		} catch (Throwable t) {
			logger.severe("Fatal error running whenFailed callback: " +ErrorStream.fromThrowable(t));
		} 
		compFuture.complete(this);
	}
	
	public Dial whenCancelled(Runnable func) {
		channelStateCancelled.add(func);
		return this;
	}
	
	private void cancelled() {
		if (wasCancelled)
			return;
		wasCancelled = true;
		computeDurationsAtEndOfCall();
		try {
			channelStateCancelled.forEach(Runnable::run);
		} catch (Throwable t) {
			logger.severe("Fatal error running whenCancelled callback: " +ErrorStream.fromThrowable(t));
		}
		compFuture.complete(this);
	}
	
	public Dial whenDisconnected(Runnable func) {
		channelStateCancelled.add(func);
		return this;
	}
	
	private void disconnected() {
		if (wasDisconnected)
			return;
		wasDisconnected = true;
		computeDurationsAtEndOfCall();
		try {
			channelStateDisconnected.forEach(Runnable::run);
		} catch (Throwable t) {
			logger.severe("Fatal error running whenDisconnected callback: " +ErrorStream.fromThrowable(t));
		} 
		compFuture.complete(this);
	}
	
	private void computeDurationsAtEndOfCall() {
		endTime = Instant.now();
		callDuration = Objects.isNull(dialStartTime) ? Duration.ZERO :
			Duration.between(dialStartTime, endTime);
		ringingDuration = (Objects.isNull(ringingTime) || Objects.isNull(answerTime)) ? Duration.ZERO :
			Duration.between(ringingTime, answerTime);
		mediaDuration = Objects.isNull(answerTime) ? Duration.ZERO :
			Duration.between(answerTime, endTime);
		logger.info("Call duration " + callDuration + " of which ringing " + ringingDuration + ", media " + mediaDuration);
	}

	/**
	 * handler for ChannelStateChange event
	 * 
	 * @param channelState new state of the channel
	 * @return
	 */
	private void handleChannelStateChanged(ChannelStateChange channelState, EventHandler<ChannelStateChange> se) {
		logger.finer("State change detected on channel " + getChannelId() + ": " + channelState.getChannel().getState());
//		if (channelState.getChannel().getState().equalsIgnoreCase("Ringing"))
//			onRinging();
	}

	/**
	 * get the duration of the call (the time passed since started dialing until the
	 * call was hanged up)
	 * 
	 * @return
	 */
	public long getCallDuration() {
		if (Objects.isNull(callDuration))
			computeDurationsAtEndOfCall();
		return callDuration.toMillis();
	}
	
	/**
	 * get media length of the call (the time passed from the moment the caller
	 * answered to the hang up of the call)
	 * 
	 * @return
	 */
	public long getMediaLength() {
		if (Objects.isNull(callDuration))
			computeDurationsAtEndOfCall();
		return mediaDuration.toMillis();
	}

	/**
	 * Retrieve the {@link CallState} of the dialed channel.
	 * @return the call state instance for the dialed channel, after it entered ARI, otherwise <code>null</code>
	 */
	public CallState getEndPoint() {
		return dialledCallState.get();
	}
	
	public long getCallEndTime() {
		return endTime.toEpochMilli();
	}

	public long getAnsweredTime() {
		return answerTime.toEpochMilli();
	}

	public String toString() {
		return "[Dial " + callerId + "->" + endpoint + "|" + 
				endpointChannelId + (Objects.nonNull(otherChannelId) ? "(local)" : "") + 
				"|" + dialStatus.name + "]";
	}

	public Channel getChannel() {
		return channel;
	}

	private <T> T mapExceptions(T val, Throwable error) {
		if (Objects.isNull(error))
			return val;
		while (error instanceof CompletionException)
			error = error.getCause();
		switch (error.getMessage()) {
		case "Channel not found": throw new ChannelNotFoundException(error);
		}
		throw new CompletionException("Unexpected Dial exception '" + error.getMessage() + "'", error);
	}

}

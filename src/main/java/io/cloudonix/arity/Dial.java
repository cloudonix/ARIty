package io.cloudonix.arity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ChannelStateChange;
import io.cloudonix.arity.errors.ErrorStream;

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
	private String endPoint;
	private String endPointChannelId = UUID.randomUUID().toString();
	private long callDuration = 0;
	private long dialStart = 0;
	private long mediaLength = 0;
	private long answeredTime = 0;
	private final static Logger logger = Logger.getLogger(Dial.class.getName());
	private transient Status dialStatus = Status.UNKNOWN;
	private Map<String, String> headers;
	private String callerId;
	private int timeout;
	private List<Runnable> channelStateUp = new ArrayList<>();
	private List<Runnable> channelStateRinging = new ArrayList<>();
	private List<Runnable> channelStateFail = new ArrayList<>();
	private int headerCounter = 0;
	private long callEndTime;
	private transient boolean ringing = false;
	private SavedEvent<ChannelStateChange> channelStateChangedSe;
	private Channel channel;
	// for local channels, which by default we don't do
	private String otherChannelId = null;
	private String extension = null;
	private String context = null;
	private Long priority = null;

	/**
	 * Constructor
	 * 
	 * @param callController
	 *            an instance that represents a call
	 * @param callerId
	 *            caller id
	 * @param destination
	 *            the number we are calling to (the endpoint)
	 * @return
	 */
	public Dial(CallController callController, String callerId, String destination) {
		this(callController, callerId, destination, new HashMap<String, String>(), -1);
	}

	/**
	 * Constructor
	 * 
	 * @param callController
	 *            an instance that represents a call
	 * @param callerId
	 *            caller id
	 * @param destination
	 *            the number we are calling to (the endpoint)
	 * @param headers
	 *            headers that we want to add when dialing
	 * @param timeout
	 *            the time we wait until for the callee to answer
	 */
	public Dial(CallController callController, String callerId, String destination, Map<String, String> headers,
			int timeout) {
		super(callController.getChannelId(), callController.getARIty());
		this.endPoint = destination;
		this.headers = headers;
		this.callerId = callerId;
		this.timeout = timeout;
	}

	/**
	 * Constructor
	 * 
	 * @param arity
	 *            instance of ARIty
	 * @param ari
	 *            instance of ARI
	 * @param callerId
	 *            caller id
	 * @param destination
	 *            the number we are calling to (the end point)
	 * @return
	 */
	public Dial(ARIty arity, String callerId, String destination) {
		this(arity, callerId, destination, new HashMap<String, String>(), -1);
	}

	/**
	 * Constructor
	 * 
	 * @param arity
	 *            instance of ARIty
	 * @param ari
	 *            instance of ari
	 * @param callerId
	 *            caller id
	 * @param destination
	 *            the number we are calling to (the endpoint)
	 * @param headers
	 *            headers that we want to add when dialing
	 * @param timeout
	 *            the time we wait until for the callee to answer
	 */
	public Dial(ARIty arity, String callerId, String destination, Map<String, String> headers, int timeout) {
		super(null, arity);
		this.endPoint = destination;
		this.headers = headers;
		this.callerId = callerId;
		this.timeout = timeout;
	}
	
	/**
	 * Override the originator channel ID for the channel to be created by Dial.
	 * 
	 * If this Dial was created with a {@link CallController}, then the originator channel ID is
	 * already set from the <tt>CallController</tt> channel ID, but this call may be used to unset it by
	 * passing <tt>null</tt> as the value.
	 * @param channelId Originator channel id
	 * @return iself for fluent calls
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
	 * The method dials to a number (end point)
	 * 
	 * @return A promise to return this instance when the dial operation completes
	 */
	public CompletableFuture<Dial> run() {
		logger.fine("Running Dial");
		getArity().ignoreChannel(endPointChannelId);
		if (Objects.nonNull(getChannelId()))
			getArity().addFutureOneTimeEvent(ChannelHangupRequest.class, getChannelId(), this::handleHangupCaller);
		getArity().addFutureOneTimeEvent(ChannelHangupRequest.class, endPointChannelId, this::handleHangupCallee);
		channelStateChangedSe = getArity().addFutureEvent(ChannelStateChange.class, endPointChannelId, this::handleChannelStateChanged);
		getArity().addFutureEvent(ch.loway.oss.ari4java.generated.Dial.class, endPointChannelId, this::handleDialEvent);

		return Operation.<Channel>retryOperation(
				cf -> channels().originate(endPoint, extension, context, priority, null, getArity().getAppName(), "",
						callerId, timeout, addSipHeaders(), endPointChannelId, otherChannelId, getChannelId(), null, cf))
				.thenAccept(channel -> {
					this.channel =  channel;
					logger.info("Dial started");
					dialStart = Instant.now().toEpochMilli();
				}).thenCompose(v -> compFuture);
	}

	/**
	 * handle Dial event
	 * 
	 * @param dial dial event
	 * @return
	 */
	private void handleDialEvent(ch.loway.oss.ari4java.generated.Dial dial, SavedEvent<ch.loway.oss.ari4java.generated.Dial>se) {
		if (dialStatus == Status.CANCEL) {
			logger.info("Dial was canceled for channel id: " + dial.getPeer().getId());
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
			answeredTime = Instant.now().toEpochMilli();
			logger.info("Channel with id: " + dial.getPeer().getId() + " answered the call");
			onConnect();
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
			Operation.<Void>retryOperation(cb -> channels().hangup(endPointChannelId, "normal", cb));
			onFail();
			compFuture.complete(this);
			se.unregister();
			return;
		case PROGRESS:
		case RINGING:
			onRinging();
			return;
		case UNKNOWN:
		}
	}

	/**
	 * set sip headers for originating new channel
	 * 
	 * @return
	 */
	private HashMap<String, String> addSipHeaders() {
		HashMap<String, String> updateHeaders = new HashMap<>();
		if (headers.isEmpty())
			return updateHeaders;

		for (Map.Entry<String, String> header : headers.entrySet()) {
			updateHeaders.put("SIPADDHEADER" + headerCounter, header.getKey() + ":" + header.getValue());
			headerCounter++;
		}
		return updateHeaders;
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
		claculateDurations();
		compFuture.complete(this);
		channelStateChangedSe.unregister();
		logger.fine("future was completed for channel: " + hangup.getChannel().getId());
	}

	/**
	 * calculate duration and media length of the call
	 */
	private void claculateDurations() {
		callEndTime = Instant.now().toEpochMilli();
		callDuration = Math.abs(callEndTime - dialStart);
		logger.info("Duration of the call: " + callDuration + " ms");
		if (Objects.nonNull(answeredTime)) {
			mediaLength = Math.abs(callEndTime - answeredTime);
			logger.info("Media lenght of the call: " + mediaLength + " ms");
		}
	}

	/**
	 * the method cancels dialing operation
	 * 
	 * @return
	 */
	@Override
	public CompletableFuture<Void> cancel() {
		logger.info("Hang up channel with id: " + endPointChannelId);
		dialStatus = Status.CANCEL;
		compFuture.complete(this);
		return Operation.<Void>retryOperation(cb -> channels().hangup(endPointChannelId, "normal", cb))
				.thenAccept(v -> logger.info("Hang up the endpoint call"));
	}

	/**
	 * get the number we are calling to
	 * 
	 * @return
	 */
	public String getEndPointNumber() {
		return endPoint;
	}

	/**
	 * set the number we are calling to
	 * 
	 * @param endPointNumber
	 */
	public void setEndPointNumber(String endPointNumber) {
		this.endPoint = endPointNumber;
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
	
	private void onRinging() {
		if(ringing)
			return;
		ringing  = true;
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
	private void onConnect() {
		onRinging();
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
	private void onFail() {
		try {
			channelStateFail.forEach(Runnable::run);
		} catch (Throwable t) {
			logger.severe("Fatal error running whenFailed callback: " +ErrorStream.fromThrowable(t));
		} 
	}

	/**
	 * handler for ChannelStateChange event
	 * 
	 * @param channelState new state of the channel
	 * @return
	 */
	private void handleChannelStateChanged(ChannelStateChange channelState, SavedEvent<ChannelStateChange> se) {
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
		return callDuration;
	}

	/**
	 * get Dial CompletableFuture
	 * 
	 * @return
	 */
	public CompletableFuture<Dial> getFuture() {
		logger.fine("endPoint channel id: " + endPointChannelId);
		return compFuture;
	}

	/**
	 * get the channel id of the dialled channel
	 * 
	 * @return
	 */
	public String getEndPointChannelId() {
		return endPointChannelId;
	}

	public long getCallEndTime() {
		return callEndTime;
	}

	/**
	 * get media length of the call (the time passed from the moment the caller
	 * answered to the hang up of the call)
	 * 
	 * @return
	 */
	public long getMediaLength() {
		return mediaLength;
	}

	public long getAnsweredTime() {
		return answeredTime;
	}
	
	public String toString() {
		return "[Dial " + callerId + "->" + endPoint + "|" + 
				endPointChannelId + (Objects.nonNull(otherChannelId) ? "(local)" : "") + 
				"|" + dialStatus.name + "]";
	}

	public Channel getChannel() {
		return channel;
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
	}
}

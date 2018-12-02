package io.cloudonix.arity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ChannelStateChange;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.Dial_impl_ari_2_0_0;

/**
 * The class represents the Dial operation
 * 
 * @author naamag
 *
 */
public class Dial extends CancelableOperations {

	private CompletableFuture<Dial> compFuture = new CompletableFuture<>();
	private String endPoint;
	private String endPointChannelId = "";
	private long callDuration = 0;
	private long dialStart = 0;
	private long mediaLength = 0;
	private long answeredTime = 0;
	private final static Logger logger = Logger.getLogger(Dial.class.getName());
	private transient String dialStatus = null;
	private Map<String, String> headers;
	private String callerId;
	private String otherChannelId = null;
	private int timeout;
	private Runnable channelStateUp = () -> {};
	private Runnable channelStateRinging = null;
	private int headerCounter = 0;
	private long callEndTime;

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
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
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
	public Dial(ARIty arity, ARI ari, String callerId, String destination) {
		this(arity, ari, callerId, destination, new HashMap<String, String>(), -1);
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
	public Dial(ARIty arity, ARI ari, String callerId, String destination, Map<String, String> headers, int timeout) {
		super(null, arity, ari);
		this.endPoint = destination;
		this.headers = headers;
		this.callerId = callerId;
		this.timeout = timeout;
	}

	/**
	 * method sets channel id's for creation of local channels
	 * 
	 * @param channelId
	 *            id of the channel we are creating for dial
	 * @param otherChannelId
	 *            other channel id when using local channels
	 * @return
	 */
	public Dial localChannelDial(String channelId, String otherChannelId) {
		this.endPointChannelId = channelId;
		this.otherChannelId = otherChannelId;
		return this;
	}

	/**
	 * The method dials to a number (end point)
	 * 
	 * @return A promise to return this instance when the dial operation completes
	 */
	public CompletableFuture<Dial> run() {
		logger.fine("Running Dial");
		if (Objects.equals(endPointChannelId, ""))
			endPointChannelId = UUID.randomUUID().toString();

		getArity().ignoreChannel(endPointChannelId);
		if (Objects.nonNull(getChannelId()))
			getArity().addFutureEvent(ChannelHangupRequest.class, getChannelId(), this::handleHangupCaller, true);
		getArity().addFutureEvent(ChannelHangupRequest.class, endPointChannelId, this::handleHangupCallee, true);
		getArity().addFutureEvent(ChannelStateChange.class, endPointChannelId, this::handleChannelStateChangedEvent,
				false);
		getArity().addFutureEvent(Dial_impl_ari_2_0_0.class, endPointChannelId, this::handleDialEvent, false);

		return this.<Channel>toFuture(
				cf -> getAri().channels().originate(endPoint, null, null, 1, null, getArity().getAppName(), null,
						callerId, timeout, addSipHeaders(), endPointChannelId, otherChannelId, null, "", cf))
				.thenAccept(channel -> {
					logger.info("Dial succeeded!");
					dialStart = Instant.now().toEpochMilli();
				}).thenCompose(v -> compFuture);
	}

	/**
	 * handle Dial event
	 * 
	 * @param dial
	 * @return
	 */
	private Boolean handleDialEvent(Dial_impl_ari_2_0_0 dial) {
		if (Objects.equals(dialStatus, "canceled")) {
			logger.info("Dial was canceled for channel id: " + dial.getPeer().getId());
			return true;
		}
		dialStatus = dial.getDialstatus();
		logger.info("Dial status of channel with id: " + dial.getPeer().getId() + "  is: " + dialStatus);
		if (!dialStatus.equals("ANSWER")) {
			if (Objects.equals(dialStatus, "BUSY") || Objects.equals(dialStatus, "NOANSWER")) {
				logger.info("The calle can not answer the call, hanging up the call");
				this.<Void>toFuture(cb -> getAri().channels().hangup(endPointChannelId, "normal", cb));
				compFuture.complete(this);
			}
			return false;
		}
		answeredTime = Instant.now().toEpochMilli();
		logger.info("Channel with id: " + dial.getPeer().getId() + " answered the call");
		onConnect();
		return true;
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
	private Boolean handleHangupCaller(ChannelHangupRequest hangup) {
		cancel();
		logger.info("Caller hanged up the call");
		return true;
	}

	/**
	 * handle hang up event of the callee
	 * 
	 * @param hangup
	 *            ChannelHangupRequest event
	 * @return
	 */
	private Boolean handleHangupCallee(ChannelHangupRequest hangup) {
		logger.info("The called endpoint hanged up the call");
		claculateDurations();
		compFuture.complete(this);
		logger.fine("future was completed for channel: " + hangup.getChannel().getId());
		return true;
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
		logger.info("hange up channel with id: " + endPointChannelId);
		if (Objects.equals(dialStatus.toLowerCase(),""))
			dialStatus = "cancelled";
		compFuture.complete(this);
		return this.<Void>toFuture(cb -> getAri().channels().hangup(endPointChannelId, "normal", cb))
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
	public String getDialStatus() {
		return dialStatus;
	}

	/**
	 * notice when channel state is Ringing
	 * 
	 * @return
	 */
	public Dial whenRinging(Runnable func) {
		channelStateRinging = func;
		return this;
	}

	/**
	 * register handler for handling when channel state is Up
	 * 
	 * @return
	 */
	public Dial whenConnect(Runnable func) {
		channelStateUp = func;
		return this;
	}
	
	private void onConnect() {
		try {
			channelStateUp.run();
		} catch (Throwable t) {
			logger.severe("Fatal error running whenConnect callback: " + t + "\n" + Stream.of(t.getStackTrace()).map(f -> f.toString()).collect(Collectors.joining("\n")));
		} 
	}

	/**
	 * handler for ChannelStateChange event
	 * 
	 * @param channelState
	 * @return
	 */
	public Boolean handleChannelStateChangedEvent(ChannelStateChange channelState) {
		if (channelState.getChannel().getState().equals("Ringing") && Objects.nonNull(channelStateRinging))
			channelStateRinging.run();
		return false;
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
	 * get the channel id of the dialed channel
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
				"|" + dialStatus + "]";
	}
}

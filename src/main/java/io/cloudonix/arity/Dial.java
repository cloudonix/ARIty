package io.cloudonix.arity;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

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

	private CompletableFuture<Dial> compFuture = new CompletableFuture<>();;
	private String endPoint;
	private String endPointChannelId = "";
	private long callDuration = 0;
	private long dialStart = 0;
	private long mediaLength = 0;
	private Instant mediaLenStart;
	private boolean isCanceled = false;
	private final static Logger logger = Logger.getLogger(Dial.class.getName());
	private String dialStatus;
	private Map<String, String> headers;
	private String callerId;
	private String otherChannelId = null;
	private int timeout = -1;
	private Runnable channelStateUp;
	private Runnable channelStateRinging;

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
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.endPoint = destination;
		this.callerId = callerId;
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
	 * method sets channel id's for creation of local channels
	 * 
	 * @param channelId
	 *            id of other channel we are creating for dial
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
	 * The method dials to a number (a sip number for now)
	 * 
	 * @return
	 */
	public CompletableFuture<Dial> run() {
		logger.fine("Running Dial");
		if (Objects.equals(endPointChannelId, ""))
			endPointChannelId = UUID.randomUUID().toString();

		// add the new channel channel id to the set of ignored Channels
		getArity().ignoreChannel(endPointChannelId);
		getArity().addFutureEvent(ChannelHangupRequest.class, getChannelId(), this::handleHangup);
		getArity().addFutureEvent(ChannelHangupRequest.class, endPointChannelId, this::handleHangup);
		getArity().addFutureEvent(ChannelStateChange.class, getChannelId(), this::handleChannelStateChangedEvent);

		getArity().addFutureEvent(Dial_impl_ari_2_0_0.class, endPointChannelId, (dial) -> {
			dialStatus = dial.getDialstatus();
			logger.info("dial status is: " + dialStatus);
			if (!dialStatus.equals("ANSWER")) {
				if (Objects.equals(dialStatus, "BUSY")) {
					isCanceled = true;
					logger.info("The calle can not answer the call, hanguing up the call");
					this.<Void>toFuture(cb -> getAri().channels().hangup(getChannelId(), "normal", cb));
				}
				return false;
			}
			mediaLenStart = Instant.now();
			return true;
		});
		logger.fine("Future event of Dial was added");

		return this
				.<Channel>toFuture(
						cf -> getAri().channels().originate(endPoint, null, null, 1, null, getArity().getAppName(),
								null, callerId, timeout, headers, endPointChannelId, otherChannelId, null, "", cf))
				.thenAccept(channel -> {
					logger.info("dial succeeded!");
					dialStart = Instant.now().toEpochMilli();
				}).thenCompose(v -> compFuture);
	}

	/**
	 * handler hang up event
	 * 
	 * @param hangup
	 *            ChannelHangupRequest event
	 * @param channelId
	 *            id of end point channel
	 * @return
	 */
	private Boolean handleHangup(ChannelHangupRequest hangup) {

		if (hangup.getChannel().getId().equals(endPointChannelId) && Objects.equals(dialStatus, "ANSWER")) {
			this.<Void>toFuture(cb -> getAri().channels().hangup(getChannelId(), "normal", cb)).thenAccept(v -> {
				logger.info("Callee hanged up the call");
				getArity().stopListeningToEvents(endPointChannelId);
			});
		}

		if (hangup.getChannel().getId().equals(getChannelId()) && !isCanceled) {
			if (Objects.equals(dialStatus, "ANSWER")) {
				claculateDurations();
				getArity().stopListeningToEvents(getChannelId());
			}
			isCanceled = true;
			cancel();
			return true;
		}

		compFuture.complete(this);
		return true;
	}

	/**
	 * calculate duration and media length of the call
	 */
	private void claculateDurations() {
		Instant end = Instant.now();
		callDuration = Math.abs(end.toEpochMilli() - dialStart);
		logger.info("Duration of the call: " + callDuration + " ms");
		mediaLength = Math.abs(end.toEpochMilli() - mediaLenStart.toEpochMilli());
		logger.info("Media lenght of the call: " + mediaLength + " ms");
	}

	/**
	 * the method cancels dialing operation
	 */
	@Override
	public void cancel() {
		if (isCanceled)
			logger.info("Caller hanged up the call");
		// hang up the call of the endpoint
		this.<Void>toFuture(cb -> getAri().channels().hangup(endPointChannelId, "normal", cb)).thenAccept(v -> {
			logger.info("Hang up the endpoint call");
			getArity().stopListeningToEvents(endPointChannelId);
			compFuture.complete(this);
		});
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
	 * @return
	 */
	public Dial whenRinging(Runnable func) {
		channelStateRinging = func;
		return this;
	}
	/**
	 * register handler for handling when channel state is Up
	 * @return
	 */
	public Dial whenConnect(Runnable func) {
		channelStateUp = func;
		return this;
	}

	public Boolean handleChannelStateChangedEvent(ChannelStateChange channelState) {
		if(channelState.getChannel().getState().equalsIgnoreCase("Up"))
			channelStateUp.run();
		if(channelState.getChannel().getState().equalsIgnoreCase("Ringing"))
			channelStateRinging.run();

		return false;
	}


}

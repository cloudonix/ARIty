package io.cloudonix.arity;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.Dial_impl_ari_2_0_0;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.HangUpException;

/**
 * The class represents the Dial operation
 * 
 * @author naamag
 *
 */
public class Dial extends CancelableOperations {

	private CompletableFuture<Dial> compFuture = new CompletableFuture<>();;
	private String endPoint;
	private String endPointChannelId;
	private long callDuration = 0;
	private long dialStart = 0;
	private long mediaLength = 0;
	private Instant mediaLenStart;
	private boolean isCanceled = false;
	private final static Logger logger = Logger.getLogger(Dial.class.getName());
	private String dialStatus;
	private Map<String, String> headers;
	private String callerId;
	
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
	public Dial(CallController callController,String callerId, String destination) {
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
	 */
	public Dial(CallController callController, String callerId, String destination, Map<String, String> headers) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.endPoint = destination;
		this.headers = headers;
		this.callerId = callerId;
	}
	
	/**
	 * The method dials to a number (a sip number for now)
	 * 
	 * @return
	 */
	public CompletableFuture<Dial> run() {
		endPointChannelId = UUID.randomUUID().toString();

		// add the new channel channel id to the set of ignored Channels
		getArity().ignoreChannel(endPointChannelId);
		getArity().addFutureEvent(ChannelHangupRequest.class, this::handleHangup);
		logger.info("future event of ChannelHangupRequest was added");

		getArity().addFutureEvent(Dial_impl_ari_2_0_0.class, (dial) -> {
			dialStatus = dial.getDialstatus();
			logger.info("dial status is: " + dialStatus);
			if (!dialStatus.equals("ANSWER"))
				return false;
			mediaLenStart = Instant.now();
			return true;
		});
		logger.info("future event of Dial was added");
		
		return this
				.<Channel>toFuture(
						cf -> getAri().channels().originate(endPoint, null, null, 1, null, getArity().getAppName(),
								null, callerId, -1, headers, endPointChannelId, null, null, "", cf))
				.thenAccept(channel -> {
					logger.info("dial succeded!");
					dialStart = Instant.now().toEpochMilli();
				}).thenCompose(v -> compFuture);
	}

	/**
	 * handler hangup event
	 * 
	 * @param hangup
	 *            ChannelHangupRequest event
	 * @return
	 */
	private Boolean handleHangup(ChannelHangupRequest hangup) {
		if (hangup.getChannel().getId().equals(getChannelId()) && !isCanceled) {
			if (Objects.equals(dialStatus, "ANSWER")) {
				cancel();
				return false;
			}
			logger.info("cancel dial");
			isCanceled = true;
			cancel();
			return false;
		}

		if (!(hangup.getChannel().getId().equals(endPointChannelId))) 
			return false;

		if (!isCanceled || Objects.equals(dialStatus, "ANSWER")) {
			// end call timer
			Instant end = Instant.now();
			callDuration = Math.abs(end.toEpochMilli() - dialStart);
			logger.info("duration of the call: " + callDuration + " ms");
			mediaLength = Math.abs(end.toEpochMilli() - mediaLenStart.toEpochMilli());
			logger.info("media lenght of the call: " + mediaLength + " ms");
		}
		compFuture.complete(this);
		return true;
	}

	/**
	 * the method cancels dialing operation
	 */
	@Override
	void cancel() {
		try {
			// hang up the call of the endpoint
			getAri().channels().hangup(endPointChannelId, "normal");
			logger.info("hang up the endpoint call");
			compFuture.complete(this);

		} catch (RestException e) {
			logger.warning("caller asked to hang up");
			compFuture.completeExceptionally(new HangUpException(e));
		}
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
	 * @return
	 */
	public String getDialStatus() {
		return dialStatus;
	}

}

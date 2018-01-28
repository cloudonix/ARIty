package io.cloudonix.arity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.Dial_impl_ari_2_0_0;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.DialException;
import io.cloudonix.arity.errors.HangUpException;

public class Dial extends CancelableOperations {
	private CompletableFuture<Dial> compFuture;
	private String endPointNumber;
	private String endPointChannelId;
	private long callDuration = 0;
	private long dialStart = 0;
	private long mediaLenght = 0;
	private long mediaLenStart = 0;
	private boolean isCanceled = false;
	private Map<String,String> sipHeadersVariables = null;
	
	private final static Logger logger = Logger.getLogger(Dial.class.getName());

	/**
	 * Constructor
	 * 
	 * @param callController
	 * @param number the number we are calling to (the endpoint)
	 */
	public Dial(CallController callController, String number) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		compFuture = new CompletableFuture<>();
		endPointNumber = number;
		sipHeadersVariables = new HashMap<String, String>();
	}

	/**
	 * The method dials to a number (a sip number for now)
	 * 
	 * @param number
	 * @return
	 */

	public CompletableFuture<Dial> run() {

		endPointChannelId = UUID.randomUUID().toString();
		String bridgeID = UUID.randomUUID().toString();

		// add the new channel channel id to the set of ignored Channels
		getArity().ignoreChannel(endPointChannelId);

		getArity().addFutureEvent(ChannelHangupRequest.class, (hangup) -> {
			
			if(hangup.getChannel().getId().equals(getChanneLID()) && !isCanceled) {
				logger.info("cancel dial");
				isCanceled = true;
				cancel();
				return false;
			}
			
			if (!(hangup.getChannel().getId().equals(endPointChannelId))) {
				return false;
			}
			
			if(!isCanceled) {
			// end call timer
			long end = Instant.now().toEpochMilli();

			callDuration = Math.abs(end-dialStart);
			logger.info("duration of the call: "+ callDuration + " ms");
			
			mediaLenght =  Math.abs(end-mediaLenStart);
			logger.info("media lenght of the call: "+ mediaLenght + " ms");

			logger.info("end point channel hanged up");
			}
			
			compFuture.complete(this);
			return true;
		});
		
		
		logger.info("future event of ChannelHangupRequest was added");
		
		
		getArity().addFutureEvent(Dial_impl_ari_2_0_0.class, (dial) -> {
			String dialStatus = dial.getDialstatus();
			logger.info("dial status is: " + dialStatus);
			
			if(!dialStatus.equals("ANSWER")) 
				return false;
			
			mediaLenStart = Instant.now().toEpochMilli();	
			//compFuture.complete(this);
			return true;
		});
		
		logger.info("future event of Dial_impl_ari_2_0_0 was added");


		// create the bridge in order to connect between the caller and end point
		// channels
		return this.<Bridge>toFuture(cf -> getAri().bridges().create("", bridgeID, "bridge", cf))
				.thenCompose(bridge -> {
					try {
						getAri().bridges().addChannel(bridge.getId(), getChanneLID(), "caller");
						logger.info(" Caller's channel was added to the bridge. Channel id of the caller:" + getChanneLID());
					
						 getAri().channels().create(endPointNumber, getArity().getAppName(), null,
								endPointChannelId, null, getChanneLID(), null);
						logger.info("end point channel was created. Channel id: " + endPointChannelId);
						
						getAri().bridges().addChannel(bridge.getId(), endPointChannelId, "callee");
						logger.info("end point channel was added to the bridge");

						return this.<Void>toFuture(dial -> {
							getAri().channels().dial(endPointChannelId, getChanneLID(), 60000, dial);
							});
						
					} catch (RestException e2) {
						logger.info("failed dailing " + e2);
						return completedExceptionally(new DialException(e2));
					}
				}).thenAccept(v -> {
					logger.info("dial succeded!");
					dialStart = Instant.now().toEpochMilli();
				}).thenCompose(v -> compFuture);

	}

	/**
	 * the method cancels dialing
	 */
	@Override
	void cancel() {

		try {
			// hang up the call of the endpoint
			getAri().channels().hangup(endPointChannelId, "normal");
			logger.info("hang up the endpoint call");
			compFuture.complete(this);

		} catch (RestException e) {
			logger.warning("failed hang up the endpoint call");
			compFuture.completeExceptionally(new HangUpException(e));
		}
	}
	
	/**
	 * set sip headers
	 * @param headers
	 */
	public void setSipHeaders(Map<String,String> headers) {
		
		if(Objects.isNull(sipHeadersVariables))
			sipHeadersVariables = new HashMap<String, String>();
		
		for (Map.Entry<String, String> currHeader : sipHeadersVariables.entrySet()) {
			sipHeadersVariables.put(currHeader.getKey(), currHeader.getValue());
		}
		
	}
	
	/**
	 * Add one sip header
	 * @param header
	 */
	public void addHeader(Map.Entry<String, String> header) {
		
		if(Objects.isNull(sipHeadersVariables))
			sipHeadersVariables = new HashMap<String, String>();

		sipHeadersVariables.put(header.getKey(), header.getValue());
		
	}

}

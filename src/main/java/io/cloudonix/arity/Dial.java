package io.cloudonix.arity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.DialException;
import io.cloudonix.arity.errors.HangUpException;

public class Dial extends CancelableOperations {
	private CompletableFuture<Dial> compFuture;
	private String endPointNumber;
	private Channel endpointChannel;

	private final static Logger logger = Logger.getLogger(Dial.class.getName());

	/**
	 * Constructor
	 * 
	 * @param call
	 * @param number

	 */
	public Dial(Call call, String number) {
		super(call.getChannelID(), call.getService(), call.getAri());
		compFuture = new CompletableFuture<>();
		endPointNumber = number;
	}

	/**
	 * The method dials to a number (a sip number for now)
	 * 
	 * @param number
	 * @return
	 */
	public CompletableFuture<Dial> run() {

		String endPointChannelId = UUID.randomUUID().toString();
		String bridgeID = UUID.randomUUID().toString();

		// create the bridge in order to connect between the caller and end point
		// channels
		getAri().bridges().create("", bridgeID, "bridge", new AriCallback<Bridge>() {

			@Override
			public void onSuccess(Bridge result) {
				logger.info("bridge was created");
			}

			@Override
			public void onFailure(RestException e) {
				logger.info("failed creating the bridge");
				compFuture.completeExceptionally(new DialException(e));
			}

		});
		// add the caller to the channel
		try {
			getAri().bridges().addChannel(bridgeID, getChanneLID(), "caller");
		} catch (RestException e1) {
			logger.info("failed adding the caller channel to the bridge");
			compFuture.completeExceptionally(new DialException(e1));
		}
		logger.info("caller's channel was added to the bridge");

		// create the end point channel (that will answer the caller)
		try {
			endpointChannel = getAri().channels().create(endPointNumber, getService().getAppName(), null, endPointChannelId, null,
					getChanneLID(), null);
			logger.info("end point channel id: " + endpointChannel.getId());
			// add the new channel channel id to the set of newCallsChannelId
			getService().setNewCallsChannelId(endPointChannelId);

		} catch (RestException e1) {
			logger.info("failed creating the end point channel");
			compFuture.completeExceptionally(new DialException(e1));
		}

		// add the end point channel to the bridge
		try {
			getAri().bridges().addChannel(bridgeID, endPointChannelId, "peer");
		} catch (RestException e1) {
			logger.info("failed adding the peer channel to the bridge");
			compFuture.completeExceptionally(new DialException(e1));
		}
		logger.info("endpoint channel was added to the bridge");

		// the caller dials to the end point
		getAri().channels().dial(endPointChannelId, getChanneLID(), 60000, new AriCallback<Void>() {

			@Override
			public void onSuccess(Void result) {
				logger.info("dial succeded!");
			}

			@Override
			public void onFailure(RestException e) {
				logger.warning("failed in dialing: " + e.getMessage());
				compFuture.completeExceptionally(new DialException(e));
			}

		});
		// add future event of ChannelHangupRequest
		getService().addFutureEvent(ChannelHangupRequest.class, (hangup) -> {
			if (!(hangup.getChannel().getId().equals(endPointChannelId))) {
				logger.info("end point channel did not asked to hang up");
				return false;
			}
			
			logger.info("end point channel hanged up");
			compFuture.complete(this);
			return true;
		});

		logger.info("future event of ChannelHangupRequest was added");

		return compFuture;
	}

	/**
	 * the method cancels dialing
 	 */
	@Override
	void cancel() {
		
		try {
			// hang up the call of the endpoint
			getAri().channels().hangup(endpointChannel.getId(), "normal");
			logger.info("dial canceled. hang up the endpoint call");
			compFuture.complete(this);
			
		} catch (RestException e) {
			logger.severe("failed hang up the endpoint call");
			compFuture.completeExceptionally(new HangUpException(e));
		}
	}

}

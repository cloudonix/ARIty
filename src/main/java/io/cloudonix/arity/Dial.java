package io.cloudonix.arity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.ChannelEnteredBridge;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.BridgeCreated_impl_ari_2_0_0;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.BridgeDestroyed_impl_ari_2_0_0;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.ChannelEnteredBridge_impl_ari_2_0_0;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.Dial_impl_ari_2_0_0;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.Conference.ConferenceState;
import io.cloudonix.arity.errors.DialException;
import io.cloudonix.arity.errors.HangUpException;

/**
 * The class represents the Dial operation
 * 
 * @author naamag
 *
 */
public class Dial extends CancelableOperations {

	private CompletableFuture<Dial> compFuture;
	private String endPointNumber;
	private String endPointChannelId;
	private long callDuration = 0;
	private long dialStart = 0;
	private long mediaLength = 0;
	private long mediaLenStart = 0;
	private boolean isCanceled = false;
	private List<Operation> nestedOperations;
	private Operation currOpertation = null;
	private final static Logger logger = Logger.getLogger(Dial.class.getName());
	private List<Conference> conferences;
	private boolean isConference = false;
	private String name = "bridge";
	private CallController cc;

	/**
	 * Constructor
	 * 
	 * @param callController
	 *            an instance that represents a call
	 * @param number
	 *            the number we are calling to (the endpoint)
	 */
	public Dial(CallController callController, String number) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		cc = callController;
		compFuture = new CompletableFuture<>();
		endPointNumber = number;
		nestedOperations = new ArrayList<>();
		conferences = new ArrayList<>();
	}

	/**
	 * Constructor for conference case
	 * 
	 * @param callController
	 *            an instance that represents a call
	 * @param number
	 *            the number we are calling to (the endpoint)
	 * @param conf
	 *            will be a part of a conference call (true if yes, false otherwise)
	 */
	public Dial(CallController callController, String number, String name, boolean conf) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		compFuture = new CompletableFuture<>();
		endPointNumber = number;
		nestedOperations = new ArrayList<>();
		isConference = conf;
		conferences = new ArrayList<>();
		this.name = name;
	}

	/**
	 * The method dials to a number (a sip number for now)
	 * 
	 * @return
	 */

	public CompletableFuture<Dial> run() {
		// check for nested verbs in Dial
		if (!nestedOperations.isEmpty()) {
			logger.info("there are verbs in the nested verb list");
			currOpertation = nestedOperations.get(0);
			CompletableFuture<? extends Operation> future = nestedOperations.get(0).run();

			if (nestedOperations.size() > 1 && Objects.nonNull(future)) {
				for (int i = 1; i < nestedOperations.size() && Objects.nonNull(future); i++) {
					currOpertation = nestedOperations.get(i);
					future = loopOperations(future);
				}
			}

		}
		endPointChannelId = UUID.randomUUID().toString();
		String bridgeID = UUID.randomUUID().toString();

		// add the new channel channel id to the set of ignored Channels
		getArity().ignoreChannel(endPointChannelId);

		getArity().addFutureEvent(ChannelHangupRequest.class, (hangup) -> {
			
			if(hangup.getChannel().getId().equals(getChannelId()) && !isCanceled) {
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
			
			mediaLength =  Math.abs(end-mediaLenStart);
			logger.info("media lenght of the call: "+ mediaLength + " ms");

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
			return true;
			
		});
		logger.info("future event of Dial_impl_ari_2_0_0.class was added");
		
		// if the created bridge can be confrence bridge, add it to confrences list
		getArity().addFutureEvent (BridgeCreated_impl_ari_2_0_0.class, (bridge)->{
			if(!isConference) {
				logger.info("the bridge is not a conference bridge");
				return true;
			}
			else {
				if(Objects.nonNull(bridge.getBridge())) {
				String confId = UUID.randomUUID().toString();
				Conference conference = new Conference(confId, getArity(), getAri());
				conference.setConfBridge(bridge.getBridge());
				conference.setCurrState(ConferenceState.Creating);
				conferences.add(conference);
				logger.info("confrence bridge was saved");
				return true;
			}
				logger.severe("conference bridge was not created");
				return false;
			}
		});
		logger.info("future event of BridgeCreated_impl_ari_2_0_0.class was added");
		
		// add channel to conference bridge
		getArity().addFutureEvent(ChannelEnteredBridge_impl_ari_2_0_0.class, (chanInBridge) ->{
			if(!isConference) {
				logger.info("channel is not a part from a conference call");
				return true;
			}
			else {
				if(Objects.nonNull(chanInBridge.getChannel())) {
					for(int i = 0; i< conferences.size(); i++) {
						if(Objects.equals(conferences.get(i).getConfBridge(), chanInBridge.getBridge())) {
							conferences.get(i).addChannelToConf(chanInBridge.getChannel(), cc);
							logger.info("channel: " +chanInBridge.getChannel().getId()+ " was added to confrence: "+ conferences.get(i).getConfName());
							return true;
						}
					}
				}
				return false;
			}
		});
		logger.info("future event of ChannelEnteredBridge_impl_ari_2_0_0.class was added");
		
		// notice when a bridge is being destroyed
		getArity().addFutureEvent(BridgeDestroyed_impl_ari_2_0_0.class, (destroyedBridge)->{
			if(!isConference)
				return true;
			else {
				for(int i = 0; i < conferences.size(); i++) {
					if(Objects.equals(conferences.get(i).getConfBridge(), destroyedBridge.getBridge())) {
						logger.info("removing conference");
						conferences.get(i).setCurrState(ConferenceState.Destroyed);
						conferences.remove(i);
						return true;
					}
				}
			}
			logger.info("no conference with bridge: " +destroyedBridge.getBridge().getId());
			return false;
		});
		logger.info("future event of BridgeDestroyed_impl_ari_2_0_0.class was added");
		
		// create the bridge in order to connect between the caller and end point
		// channels
		return this.<Bridge>toFuture(cf -> getAri().bridges().create("", bridgeID, name, cf))
				.thenCompose(bridge -> {
					try {
						getAri().bridges().addChannel(bridge.getId(), getChannelId(), "caller");
						logger.info(" Caller's channel was added to the bridge. Channel id of the caller:" + getChannelId());
						
						getAri().channels().create(endPointNumber, getArity().getAppName(), null,
								endPointChannelId, null, getChannelId(), null);
						logger.info("end point channel was created. Channel id: " + endPointChannelId);
						
						getAri().bridges().addChannel(bridge.getId(), endPointChannelId, "callee");
						logger.info("end point channel was added to the bridge");
						
						return this.<Void>toFuture(dial -> {
							getAri().channels().dial(endPointChannelId, getChannelId(), 60000, dial);
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
			logger.warning("failed hang up the endpoint call");
			compFuture.completeExceptionally(new HangUpException(e));
		}
	}

	/**
	 * compose the previous future (of the previous verb) to the result of the new
	 * future
	 * 
	 * @param future
	 * @param operation
	 * @return
	 */
	private CompletableFuture<? extends Operation> loopOperations(CompletableFuture<? extends Operation> future) {
		logger.info("The current nested operation is: " + currOpertation.toString());
		return future.thenCompose(op -> {
			if (Objects.nonNull(op))
				return currOpertation.run();
			return CompletableFuture.completedFuture(null);
		});
	}

	/**
	 * add new operation to list of nested operation that run method will execute
	 * one by one
	 * 
	 * @param operation
	 * @return
	 */

	public Dial and(Operation operation) {
		nestedOperations.add(operation);
		return this;
	}

}

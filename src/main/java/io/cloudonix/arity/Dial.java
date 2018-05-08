package io.cloudonix.arity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.BridgeCreated_impl_ari_2_0_0;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.ChannelEnteredBridge_impl_ari_2_0_0;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.ChannelLeftBridge_impl_ari_2_0_0;
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

	private CompletableFuture<Dial> compFuture = new CompletableFuture<>();;
	private String endPoint;
	private String endPointChannelId;
	private long callDuration = 0;
	private long dialStart = 0;
	private long mediaLength = 0;
	private Instant mediaLenStart;
	private boolean isCanceled = false;
	private final static Logger logger = Logger.getLogger(Dial.class.getName());
	private List<Conference> conferences = new ArrayList<>();
	private String name = "bridge";
	private String dialStatus;
	private Map<String,String> headers = null;

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
		endPoint = number;
	}
	
	/**
	 * Constructor
	 * 
	 * @param callController
	 *            an instance that represents a call
	 * @param number
	 *            the number we are calling to (the endpoint)
	 *  @param headers 
	 *  			headers that we want to add when dialing 
	 */
	public Dial(CallController callController, String number, Map<String,String> headers) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.endPoint = number;
		this.headers = headers;
	}

	/**
	 * Constructor for conference case
	 * 
	 * @param callController
	 *            an instance that represents a call
	 * @param number
	 *            the number we are calling to (the endpoint)
	 * @return
	 */
	public Dial(CallController callController, String number, String name) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		endPoint = number;
		this.name = name;
	}

	/**
	 * The method dials to a number (a sip number for now)
	 * 
	 * @return
	 */
	public CompletableFuture<Dial> run() {
		endPointChannelId = UUID.randomUUID().toString();
		String bridgeID = UUID.randomUUID().toString();

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
		logger.info("future event of Dial_impl_ari_2_0_0.class was added");

		getArity().addFutureEvent(BridgeCreated_impl_ari_2_0_0.class, this::bridgeCreated);
		logger.info("future event of BridgeCreated_impl_ari_2_0_0.class was added");

		getArity().addFutureEvent(ChannelEnteredBridge_impl_ari_2_0_0.class, (chanInBridge) -> {
			if (Objects.equals(chanInBridge.getChannel().getId(), getChannelId())) {
				logger.info("try adding caller to conference");
				return channelEnteredBridge(chanInBridge);
			}
			if (Objects.equals(chanInBridge.getChannel().getId(), endPointChannelId)) {
				return channelEnteredBridge(chanInBridge);
			}
			return false;
		});
		logger.info("future event of ChannelEnteredBridge_impl_ari_2_0_0.class was added");

		// create the bridge in order to connect between the caller and end point
		// channels
		return this.<Bridge>toFuture(cf -> getAri().bridges().create("", bridgeID, name, cf)).thenCompose(bridge -> {
			try {
				getAri().bridges().addChannel(bridge.getId(), getChannelId(), "caller");
				logger.info(" Caller's channel was added to the bridge. Channel id of the caller:" + getChannelId());

				getAri().channels().create(endPoint, getArity().getAppName(), null, endPointChannelId, null, getChannelId(), null);
				
				logger.info("end point channel creation to resource: " + endPoint);

				// originateWithId(String channelId, String endpoint, String extension, String context, long priority, String label, String app, String appArgs, String callerId, int timeout, Map<String,String> variables, String otherChannelId, String originator, String formats)
				//getAri().channels().originateWithId(endPointChannelId, endPoint, null, null ,1 , null, getAri().getAppName(), null, getChannelId(),-1 ,headers, null, getChannelId(),"");
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
	 * handle bridge created event
	 * 
	 * @param bridge BridgeCreated event
	 * @return
	 */
	private boolean bridgeCreated(BridgeCreated_impl_ari_2_0_0 bridge) {
			if (Objects.nonNull(bridge.getBridge())) {
				Conference conference = new Conference(UUID.randomUUID().toString(), getArity(), getAri());
				conference.setConfBridge(bridge.getBridge());
				logger.info("brige id: " + bridge.getBridge().getId());
				conferences.add(conference);
				logger.info("confrence bridge was saved");
				return true;
			}
			logger.severe("conference bridge was not created");
			return false;
	}

	/**
	 * handler hangup event
	 * @param hangup ChannelHangupRequest event
	 * @return
	 */
	private Boolean handleHangup(ChannelHangupRequest hangup) {
		// notice when a channel leaves a bridge after a hang up occurred
		getArity().addFutureEvent(ChannelLeftBridge_impl_ari_2_0_0.class, (channelLeft) -> {
			if (conferences.size() == 1 && conferences.get(0).getChannelsInConf().size() == 0) {
				logger.info("1 conference with no participents. remove it");
				conferences.remove(0);
				getArity().getCallSupplier().get().setConferences(conferences);
				return true;
			}
			for (int i = 0; i < conferences.size(); i++) {
				for (int j = 0; j < conferences.get(i).getChannelsInConf().size(); j++) {
					if (Objects.equals(conferences.get(i).getChannelsInConf().get(j).getId(),
							channelLeft.getChannel().getId())) {
						logger.info("removing channel: " + channelLeft.getChannel().getId() + " from conference");
						conferences.get(i).removeChannelFromConf(channelLeft.getChannel());
						if (conferences.get(i).getCount() < 2) {
							logger.info("conference call was completed, remove it from the list");
							conferences.remove(i);
							getArity().getCallSupplier().get().setConferences(conferences);
							return true;
						}
					}
				}
			}
			logger.info("no conference contains the channel: " + channelLeft.getChannel().getId());
			return false;
		});
		logger.info("future event of ChannelLeftBridge_impl_ari_2_0_0.class was added");

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
			logger.info("end point channel hanged up");
		}

		compFuture.complete(this);
		return true;
	}

	/**
	 * handle a channel entered bridge event
	 * 
	 * @param chanInBridge
	 *            instance of channelEnteredBridge event
	 * @param channelId 
	 * @return
	 */
	private boolean channelEnteredBridge(ChannelEnteredBridge_impl_ari_2_0_0 chanInBridge) {
		for (int i = 0; i < conferences.size(); i++) {
			if (Objects.equals(conferences.get(i).getConfBridge().getId(), chanInBridge.getBridge().getId())) {
				conferences.get(i).setNewChannel(chanInBridge.getChannel());
				conferences.get(i).setConfName(name);
				conferences.get(i).addChannelToConf(chanInBridge.getChannel());
				logger.info("channel: " + chanInBridge.getChannel().getId() + " was added to confrence: "
						+ conferences.get(i).getConfName());
				if (conferences.get(i).getCount() == 2) {
					conferences.get(i).setCurrState(ConferenceState.Ready);
					// update call controller conferences list as well
					getArity().getCallSupplier().get().setConferences(conferences);
					logger.info("conference is ready");
					return true;
				}
				return true;
			}
		}
		return false;
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
	 * get list of conferences
	 * 
	 * @return
	 */
	public List<Conference> getConferences() {
		return conferences;
	}

	/**
	 * set list of confernces
	 * 
	 * @param conferences
	 */
	public void setConferences(List<Conference> conferences) {
		this.conferences = conferences;
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

}

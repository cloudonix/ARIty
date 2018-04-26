package io.cloudonix.arity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.generated.Variable;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;

/**
 * The class represents a call controller, including all the call operation and
 * needed information for a call
 * 
 * @author naamag
 *
 */
public abstract class CallController implements Runnable {

	private CallState state = new CallState();
	// sip headers that were added by request, not part of the existing headers
	private Map<String, String> addedSipHeaders = null;
	private final static Logger logger = Logger.getLogger(CallController.class.getName());
	private List<Conference> conferences = null;

	/**
	 * Initialize the callController with the needed fields
	 * 
	 * @param ss
	 *            StasisStart
	 * @param a
	 *            ARI
	 * @param ARIty
	 *            ARITY
	 */
	public void init(StasisStart ss, ARI a, ARIty ARIty) {
		state.setCallStasisStart(ss);
		state.setAri(a);
		state.setArity(ARIty);
		state.setChannel(ss.getChannel());
		state.setChannelID(ss.getChannel().getId());
		addedSipHeaders = new HashMap<String, String>();
	}

	/**
	 * get the StasisStart from the call
	 * 
	 * @return
	 */
	public StasisStart getCallStasisStart() {
		return state.getCallStasisStart();
	}

	/**
	 * get the ari from the call
	 * 
	 * @return
	 */
	public ARI getAri() {
		return state.getAri();
	}

	/**
	 * get the service from the call
	 * 
	 * @return
	 */
	public ARIty getARItyService() {
		return state.getArity();
	}

	/**
	 * get the channel id of the call
	 * 
	 * @return
	 */
	public String getChannelID() {
		return state.getChannelID();
	}

	/**
	 * The method creates a new Play operation with the file to be played
	 * 
	 * @param file
	 *            file to be played
	 * @return Play
	 */
	public Play play(String file) {
		return new Play(this, file);
	}

	/**
	 * the method creates a new Answer operation to answer the call
	 * 
	 * @return Answer
	 */
	public Answer answer() {
		return new Answer(this);
	}

	/**
	 * the method creates a new HangUp operation to hang up the call
	 * 
	 * @return HangUp
	 */
	public Hangup hangup() {
		return new Hangup(this);
	}

	/**
	 * the method creates a new receivedDTMF object
	 * 
	 * @return ReceivedDTMF
	 */
	public ReceivedDTMF receivedDTMF(String terminatingKey) {
		return new ReceivedDTMF(this, terminatingKey);
	}

	/**
	 * the method creates a new receivedDTMF operation
	 * 
	 * @return Gather
	 */
	public ReceivedDTMF receivedDTMF() {
		return new ReceivedDTMF(this);
	}

	/**
	 * the method created new Dial operation
	 * 
	 * @param number
	 *            the number of the endpoint (who are we calling to)
	 * @return
	 */
	public Dial dial(String number) {
		Dial dial = new Dial(this, number);
		conferences = dial.getConferences();
		return dial;

	}

	/**
	 * the method created new Dial operation
	 * 
	 * @param number
	 *            the number of the endpoint (who are we calling to)
	 * @param conf
	 *            the call will be available for a conference call (true if yes,
	 *            otherwise if false)
	 * @param confName
	 *            name of the conference (bridge)
	 * @return
	 */
	public Dial dial(String number, String confName) {
		Dial dial = new Dial(this, number, confName);
		conferences = dial.getConferences();
		return dial;
	}

	/**
	 * the method creates a new Conference
	 * 
	 *
	 * @return
	 */
	public Conference conference() {
		return new Conference(UUID.randomUUID().toString(), state.getArity(), state.getAri());
	}

	/**
	 * the method verifies that the call is always hangs up, even if an error
	 * occurred during any operation
	 * 
	 * @param value
	 *            if no error occurred
	 * @param th
	 *            if an error occurred it will contain the error
	 * @return
	 */
	public <V> CompletableFuture<V> endCall(V value, Throwable th) {
		return hangup().run().handle((hangup, th2) -> null)
				.thenCompose(v -> Objects.nonNull(th) ? Operation.completedExceptionally(th)
						: CompletableFuture.completedFuture(value));
	}

	/**
	 * get the value of a specific sip header
	 * 
	 * @param headerName
	 *            the name of the header, for example: To
	 * @return
	 * @throws RestException
	 */
	public CompletableFuture<String> getSipHeader(String headerName) {
		return this
				.<Variable>futureFromAriCallBack(
						cb -> state.getAri().channels().getChannelVar(state.getChannelID(), "SIP_HEADER(" + headerName + ")", cb))
				.thenApply(v -> {
					return v.getValue();
				}).exceptionally(t -> {
					logger.fine("unable to find header: " + headerName);
					return null;
				});
	}

	/**
	 * helper method for getSipHeader in order to have AriCallback
	 * 
	 * @param consumer
	 * @return
	 */
	private <V> CompletableFuture<V> futureFromAriCallBack(Consumer<AriCallback<V>> consumer) {
		Exception e = new Exception();
		e.fillInStackTrace();
		StackTraceElement[] st = e.getStackTrace();
		CompletableFuture<V> compFuture = new CompletableFuture<V>();

		consumer.accept(new AriCallback<V>() {

			@Override
			public void onSuccess(V result) {
				compFuture.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				Exception e1 = new Exception(e);
				e1.setStackTrace(st);
				compFuture.completeExceptionally(e1);
			}
		});
		return compFuture;
	}

	/**
	 * add a sip header
	 * 
	 * @param headerName
	 *            the name of the header, for example: (To)
	 * @param headerValue
	 *            the value of the header
	 * @throws RestException
	 */
	public void addSipHeader(String headerName, String headerValue) throws RestException {
		// ari.channels().getChannelVar(channelID, headerName).setValue(headerValue);
		addedSipHeaders.put("SIP_HEADER(" + headerName + ")", headerValue);
	}

	/**
	 * get list of conference calls
	 * 
	 * @return
	 */
	public List<Conference> getConferences() {
		return conferences;
	}

	/**
	 * set list of conference calls
	 * 
	 * @param conferences
	 *            update list of conference calls
	 * @return
	 */
	public void setConferences(List<Conference> conferences) {
		this.conferences = conferences;
	}

	/**
	 * check if there is a conference with a specific name
	 * 
	 * @param name
	 *            name of the conference we are looking for
	 * @return
	 */
	public boolean isConferenceWithName(String name) {
		for (int i = 0; i < conferences.size(); i++) {
			if (Objects.equals(conferences.get(i).getConfName(), name)) {
				logger.info("conference with name: " + name + " exists");
				return true;
			}
		}
		logger.info("no conference with name: " + name);
		return false;
	}

	/**
	 * get the channel of the call
	 * @return
	 */
	public Channel getChannel() {
		return state.getChannel();
	}

	/**
	 * set the channel of the call
	 * @param channel channel to update
	 */
	public void setChannel(Channel channel) {
		state.setChannel(channel);
	}
	
	/**
	 * the method return the extension from the dialplan
	 * @return
	 */
	public String getExtension () {
		return state.getCallStasisStart().getChannel().getDialplan().getExten();
	}
	
	/**
	 * get the call controller in order to give it to another call controller
	 * @return
	 */
	public CallController handOverCall() {
		return this;
	}

}

package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.InvalidCallStateException;
import io.cloudonix.lib.Futures;

/**
 * The class represents a call controller, including all the call operation and
 * needed information for a call
 *
 * @author naamag
 *
 */
public abstract class CallController {
	private CallState callState = new CallState();
	private Logger logger = Logger.getLogger(getClass().getName());

	/**
	 * Initialize the call controller with an existing transferable call state.
	 *
	 * This method is only called internally.
	 * @param callState call state of the call to be executed on
	 */
	void init(CallState callState) {
		this.callState = callState;
		initLogger();
		init();
	}

	/**
	 * Called just before the call controller is {@link #run()}.
	 * Implementations may wish to override this method to run their own initialization logic.
	 * When this method is called, the {@link #callState} has already been initialized.
	 * By default this method does nothing, so there's no need for implementations to call
	 * <tt>super</tt>.
	 */
	protected void init() {
	}

	private void initLogger() {
		logger = Logger.getLogger(getClass().getName() + ":" + callState.getChannelId());
	}

	/**
	 * get the service from the call
	 *
	 * @return
	 */
	public ARIty getARIty() {
		return callState.getArity();
	}

	/**
	 * get the channel id of the call
	 *
	 * @return
	 */
	public String getChannelId() {
		return callState.getChannelId();
	}

	/**
	 * play media to a channel
	 *
	 * @param file file to be played
	 * @return
	 */
	public Play play(String file) {
		return new Play(this, file);
	}

	/**
	 * play stored recording to a channel
	 *
	 * @param recName recording name to play
	 * @return
	 */
	public Play playRecording(String recName) {
		Play playRecording = new Play(this, recName);
		playRecording.playRecording();
		return playRecording;
	}

	/**
	 * Create an answer operation for the current call, if it was not already answered.
	 *
	 * If the call was already answered when calling this method, it will generate an "no-op"
	 * answer operation that just immediately complete. Please note that if you call this method
	 * before the channel has been answered, then it was ansered in another way, then you run the
	 * created <tt>Answer</tt> operation, the additional answer will block until the call disconnects
	 * @return
	 */
	public Answer answer() {
		if (getCallState().wasAnswered())
			return new Answer(this) {
				@Override
				public CompletableFuture<Answer> run() {
					return CompletableFuture.completedFuture(this);
				}
			};
		return new Answer(this);
	}

	/**
	 * the method creates a new HangUp operation to hang up the call
	 *
	 * @return
	 */
	public Hangup hangup() {
		return new Hangup(this);
	}

	/**
	 * the method creates a new Ring operation
	 *
	 */
	public Ring ring() {
		return new Ring(this);
	}

	/**
	 * the method creates a new receivedDTMF object
	 *
	 * @param terminatingKey terminating key for stop receiving DTMF
	 * @param inputLength    length of the input that we expect receiving from the
	 *                       caller
	 * @return
	 */
	public ReceivedDTMF receivedDTMF(String terminatingKey, int inputLength) {
		return new ReceivedDTMF(this, terminatingKey, inputLength);
	}

	/**
	 * the method creates a new received DTMF operation with default values
	 *
	 * @return
	 */
	public ReceivedDTMF receivedDTMF() {
		return new ReceivedDTMF(this);
	}

	/**
	 * Create a dial out operation
	 * @param callerId Caller ID to present to the destination
	 * @param destination Asterisk endpoint address to dial (including technology and URL)
	 * @return Dial operation to be configured further and run
	 */
	public Dial dial(String callerId, String destination) {
		return new Dial(this, callerId, destination);
	}

	/**
	 * get conference according to it's bridge id
	 *
	 * @param bridgeId id of conference bridge we want to get
	 */
	public CompletableFuture<Conference> getConference(String bridgeId) {
		Conference conf = new Conference(this, bridgeId);
		return CompletableFuture.completedFuture(conf);
	}

	/**
	 * create a new conference with
	 *
	 * @param conferenceName name of the conference
	 * @return
	 */
	public CompletableFuture<Conference> createConference(String conferenceName) {
		Conference conf = new Conference(this);
		return conf.createConferenceBridge(conferenceName).thenApply(b->conf);
	}

	/**
	 * the method verifies that the call is always hangs up, even if an error
	 * occurred during any operation
	 *
	 * @param value if no error occurred
	 * @param th    if an error occurred it will contain the error
	 * @return
	 */
	public <V> CompletableFuture<V> endCall(V value, Throwable th) {
		return hangup().run().handle((hangup, th2) -> null)
				.thenCompose(v -> Objects.nonNull(th) ? CompletableFuture.failedFuture(th)
						: CompletableFuture.completedFuture(value));
	}

	/**
	 * Update a channel variable on the call state.
	 * @param name name of the variable to update
	 * @param value value of the variable to update
	 * @return
	 */
	public CompletableFuture<Void> setVariable(String name, String value) {
		return callState.setVariable(name, value);
	}

	/**
	 * Read a channel variable from the call state, possibly loading it from ARI
	 * @param name Variable to retrieve
	 * @return A promise for a variable value. The promise may resolve to <code>null</code> if the variable
	 *   is not set.
	 */
	public CompletableFuture<String> getVariable(String name) {
		return callState.readVariable(name);
	}

	/**
	 * get the value of a specific sip header
	 *
	 * @param headerName the name of the header, for example: To
	 * @return
	 * @throws RestException
	 */
	public CompletableFuture<String> getSipHeader(String headerName) {
		return callState.readVariable("SIP_HEADER(" + headerName + ")");
	}

	/**
	 * get the value of a specific PJSIP header
	 *
	 * @param headerName the name of the header, for example: To
	 * @return
	 * @throws RestException
	 */
	public CompletableFuture<String> getPJSipHeader(String headerName) {
		return callState.readVariable("PJSIP_HEADER(" + headerName + ")");
	}

	/**
	 * add sip header to a channel
	 *
	 * @param headerName  name of the new header
	 * @param headerValue value of the new header
	 * @return
	 */
	public CompletableFuture<Void> setSipHeader(String headerName, String headerValue) {
		return callState.setVariable("SIP_HEADER(" + headerName + ")", headerValue);
	}

	/**
	 * add pjsip header to a channel
	 *
	 * @param headerName  name of the new header
	 * @param headerValue value of the new header
	 * @return
	 */
	public CompletableFuture<Void> setPJSipHeader(String headerName, String headerValue) {
		return callState.setVariable("PJSIP_HEADER(" + headerName + ")", headerValue);
	}

	/**
	 * change setting regarding to TALK_DETECT function
	 *
	 * @param action      'set' or 'remove'
	 * @param actionValue if set action is used, action value will be in the form:
	 *                    'threshold1,threshold2' such that threshold 1 is the time
	 *                    in milliseconds before which a user is considered silent.
	 *                    and threshold 2 is the time in milliseconds after which a
	 *                    user is considered talking. use the empty string for no
	 *                    threshold
	 * @return
	 */
	public CompletableFuture<Void> setTalkingInChannel(String action, String actionValue) {
		return Operation.<Void>retry(cb -> callState.getAri().channels()
				.setChannelVar(callState.getChannelId(), "TALK_DETECT(" + action + ")").setValue(actionValue).execute(cb))
				.exceptionally(t -> {
					logger.info("Unable to " + action + " with value " + actionValue + ": " + t);
					return null;
				});

	}

	/**
	 * get the channel of the call
	 *
	 * @return
	 */
	public Channel getChannel() {
		return callState.getChannel();
	}

	/**
	 * the method return the extension from the dialplan
	 *
	 * @return
	 */
	public String getExtension() {
		return Objects.nonNull(getChannel()) && Objects.nonNull(getChannel().getDialplan())
				? String.valueOf(getChannel().getDialplan().getExten())
				: null;
	}

	/**
	 * return account code of the channel (information about the channel)
	 *
	 * @return
	 */
	public String getAccountCode() {
		return Objects.nonNull(getChannel()) ? String.valueOf(getChannel().getAccountcode()) : null;
	}

	/**
	 * get the caller (whom is calling)
	 *
	 * @return
	 */
	public String getCallerIdNumber() {
		return Objects.nonNull(getChannel()) && Objects.nonNull(getChannel().getCaller())
				? callState.getChannel().getCaller().getNumber()
				: null;
	}

	/**
	 * get the name of the channel (for example: SIP/myapp-000001)
	 *
	 * @return
	 */
	public String getChannelName() {
		return Objects.nonNull(getChannel()) ? getChannel().getName() : null;
	}

	/**
	 * return channel state
	 *
	 * @return
	 */
	public String getChannelState() {
		return Objects.nonNull(getChannel()) ? String.valueOf(getChannel().getState()) : null;
	}

	/**
	 * get channel creation time
	 *
	 * @return
	 */
	public String getChannelCreationTime() {
		return Objects.nonNull(getChannel()) ? String.valueOf(getChannel().getCreationtime()) : null;

	}

	/**
	 * return dialplan context (for example: ari-context)
	 *
	 * @return
	 */
	public String getDialplanContext() {
		return Objects.nonNull(getChannel()) && Objects.nonNull(getChannel().getDialplan())
				? getChannel().getDialplan().getContext()
				: null;
	}

	/**
	 * Retrieve the current priority of the extension that call this stasis application
	 * @return priority number
	 */
	public long getPriority() {
		return Objects.nonNull(getChannel()) && Objects.nonNull(getChannel().getDialplan())
				? getChannel().getDialplan().getPriority()
				: null;
	}

	/**
	 * Store custom data in the transferable call state
	 * @param dataName    name of the data field to store
	 * @param dataContent Data to store
	 */
	public void put(String dataName, Object dataContent) {
		callState.put(dataName, dataContent);
	}

	/**
	 * Load custom data from the transferable call state
	 *
	 * The data will be cast to the expected data type, so make sure you always store and load the same type
	 * for the same field name
	 *
	 * @param dataName name of the data field to load
	 * @return the value stored, casted to the expected type
	 */
	public <T> T get(String dataName) {
		return callState.get(dataName);
	}

	/**
	 * Check if specific custom data field was stored in the transferable call state
	 * @param dataName name of the data field to check
	 * @return Whether the field has been previously stored in the call state, even if its value was stored as <tt>null</tt>
	 */
	public boolean contains(String dataName) {
		return callState.contains(dataName);
	}

	/**
	 * Retrieve the current call state
	 * @return the current call state object
	 */
	public CallState getCallState() {
		if (Objects.isNull(callState))
			throw new InvalidCallStateException();
		return callState;
	}

	/**
	 * create record operation with more settings
	 *
	 * @param callController instant representing the call
	 * @param name           Recording's filename
	 * @param format         Format to encode audio in (wav, gsm..)
	 * @param maxDuration    Maximum duration of the recording, in seconds. 0 for no
	 *                       limit
	 * @param maxSilence     Maximum duration of silence before ending the
	 *                       recording, in seconds. 0 for no limit
	 * @param beep           true if we want to play beep when recording begins,
	 *                       false otherwise
	 * @param termKey        DTMF input to terminate recording (allowed values:
	 *                       none, any, *, #)
	 * @return
	 */
	public Record record(String name, String format, int maxDuration, int maxSilence, boolean beep, String termKey) {
		return new Record(this, name, format, maxDuration, maxSilence, beep, termKey);
	}

	/**
	 * Mute audio for channel
	 *
	 * @param channelId id of the channel we want to mute
	 * @param direction audio direction of the mute. Allowed values: both, in, out
	 * @return
	 */
	public Mute mute(String channelId, String direction) {
		return new Mute(this, channelId, direction);
	}

	/**
	 * Create bridge instance to handle all bridge operations
	 *
	 * @param arity instance of ARIty
	 * @return
	 */
	public Bridge bridge(ARIty arity) {
		return new Bridge(arity);
	}

	/**
	 * transfer Call Controller to the next Call controller
	 *
	 * @param nextCallController Call Controller that we are getting the data from
	 */
	public CompletableFuture<Void> execute(CallController nextCallController) {
		nextCallController.init(callState);
		return nextCallController.run();
	}

	/**
	 * if the channel is still active return true, false otherwise
	 *
	 * @param channelId channel id of the call to be checked
	 * @return
	 * @throws RestException
	 */
	public CompletableFuture<Boolean> isCallActive(String channelId) {
		return Operation.<Channel>retry(cb -> callState.getAri().channels().get(channelId).execute(cb))
				.thenApply(result -> {
					logger.info("Call with id: " + result.getId() + " is still active");
					return true;
				})
				.exceptionally(Futures.on(RestException.class, e -> {
					logger.info("Call is not active ");
					return false;
				}));
	}


	public abstract CompletableFuture<Void> run();

	/**
	 * create a new channel redirect operation
	 *
	 * @param channelId the id of the channel we redirecting
	 * @param endpoint the endpoint to redirect the channel to
	 *
	 * @return
	 */
	public Redirect redirect(String channelId, String endpoint) {
		return new Redirect(channelId, this.getARIty(), endpoint);
	}
}

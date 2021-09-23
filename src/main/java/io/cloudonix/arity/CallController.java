package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ch.loway.oss.ari4java.generated.models.Message;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.InvalidCallStateException;
import io.cloudonix.arity.models.AsteriskBridge;
import io.cloudonix.arity.models.AsteriskChannel;
import io.cloudonix.arity.helpers.Futures;

/**
 * The main implementation for ARIty based program logic - applications should subclass the {@link CallController}
 * to implement their own logic.
 * 
 * You can think of a call controller as a context in a dial plan - it is triggered by Asterisk to implement a set
 * of logic operations, part of which might be calling other CallControllers.
 * 
 * The CallController provides APIs for the application to:
 * <ul>
 * <li>Pass control to another call controller and get it back when it is done (unlike Asterisk dialplan extensions that
 * can control flow with "goto", ARIty call controllers {@link #execute(CallController)} other controllers and then receive
 * control back when they are done).</li>
 * <li>Store and retrieve dynamic state using {@link #get(String)}, {@link #put(String, Object)} and {@link #contains(String)}.
 * When delegating control to other call controllers, the dynamic state is automatically shared to the executing controllers.</li>
 * <li>Easy access to read and write Asterisk channel variables with a local cache (so that multiple reads of the same value don't
 * need to hit the ARI API multiple times) using {@link #getVariable(String)} and {@link #setVariable(String, String)}</li>
 * <li>Easy access to ARIty operations that will be automatically invoked on the controller's channel, such as {@link #play(String)}
 * or {@link #record(String, String, int, int, boolean, String)}</li>
 * </ul> 
 *
 * @author odeda
 * @author naamag
 */
public abstract class CallController {
	private final String ARITY_BOUND_BRIDGE = "arity-bound-bridge";
	private CallState callState = new CallState();
	private Logger logger = LoggerFactory.getLogger(getClass());
	private Marker logmarker;

	/**
	 * Initialize the call controller with an existing transferable call state.
	 *
	 * This method is only called internally.
	 * @param callState call state of the call to be executed on
	 */
	void init(CallState callState) {
		this.callState = callState;
		logmarker = MarkerFactory.getDetachedMarker(callState.getChannelId());
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
	 * Bind this call to a bridge - i.e. create a mixing bridge that is associated with this channel and have all ARIty
	 * operations (such as {@link #play(String)} and {@link #dial(String, String)} operate on the bridge instead of
	 * directly on the channel (this also forces the "Early Bridging" behavior of <code>dial()</code>, see {@link Dial#withBridge(Bridge)}).
	 * 
	 * @return A promise that will be fulfilled when the channel is bound to a new bridge.
	 */
	public CompletableFuture<Void> bindToBridge() {
		if (isBoundToBridge())
			return CompletableFuture.completedFuture(null);
		return getARIty().bridges().create("arity-bind-" + getChannelId()).thenCompose(bridge -> {
			callState.put(ARITY_BOUND_BRIDGE, bridge);
			return bridge.addChannel(getChannelId(), true);
		});
	}
	
	/**
	 * Check whether this call is bound to a bridge.
	 * @see #bindToBridge()
	 * @return true if the call is bound to a bridge
	 */
	public boolean isBoundToBridge() {
		return callState.contains(ARITY_BOUND_BRIDGE);
	}
	
	/**
	 * Retrieve the bridge this call is bound to.
	 * @return a {@link Bridge} instance if the call is bound to a bridge, <code>null</code> otherwise
	 */
	public AsteriskBridge getBoundBridge() {
		return callState.<AsteriskBridge>get(ARITY_BOUND_BRIDGE);
	}

	/**
	 * play media to a channel
	 *
	 * @param file file to be played
	 * @return
	 */
	public Play play(String file) {
		return new Play(this, file).withBridge(getBoundBridge());
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
	 * Create a new DTMF receiver with both key and max length stop condition
	 * @param terminatingKey terminating key for stop receiving DTMF
	 * @param maxLength    maximum length of input we expect from the caller
	 * Make sure you cancel this DTMF receiver before terminating the call, otherwise this operation will never end and will leak
	 * @return a DTMF receiver operation
	 */
	public ReceiveDTMF receiveDTMF(String terminatingKey, int maxLength) {
		return new ReceiveDTMF(this, terminatingKey, maxLength);
	}

	/**
	 * Create a new DTMF receiver with a terminating key stop condition
	 * @param terminatingKey terminating key for stop receiving DTMF
	 * Make sure you cancel this DTMF receiver before terminating the call, otherwise this operation will never end and will leak
	 * @return a DTMF receiver operation
	 */
	public ReceiveDTMF receiveDTMF(String terminatingKey) {
		return new ReceiveDTMF(this, terminatingKey);
	}

	/**
	 * Create a new DTMF receiver with a max length stop condition
	 * @param maxLength    maximum length of input we expect from the caller
	 * Make sure you cancel this DTMF receiver before terminating the call, otherwise this operation will never end and will leak
	 * @return a DTMF receiver operation
	 */
	public ReceiveDTMF receiveDTMF(int maxLength) {
		return new ReceiveDTMF(this, maxLength);
	}

	/**
	 * Create a new DTMF receiver with no stop conditions
	 * Make sure you cancel this DTMF receiver before terminating the call, otherwise this operation will never end and will leak
	 * @return a DTMF receiver operation
	 */
	public ReceiveDTMF receiveDTMF() {
		return new ReceiveDTMF(this);
	}

	/**
	 * Create a dial out operation
	 * @param callerId Caller ID to present to the destination
	 * @param destination Asterisk endpoint address to dial (including technology and URL)
	 * @return Dial operation to be configured further and run
	 */
	public Dial dial(String callerId, String destination) {
		Dial dial = new Dial(this, callerId, destination);
		if (getBoundBridge() != null)
			dial.withBridge(getBoundBridge());
		return dial;
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
	
	public enum DenoiseDirection { rx, tx }
	public CompletableFuture<Void> denoiseFilter(DenoiseDirection direction, boolean enable) {
		return setVariable("DENOISE(" + direction.name() + ")", enable ? "on" : "off");
	}

	/**
	 * Enable or disable talk detection using TALK_DETECTION function
	 * @param enable whether to enable or disable talking detection using the built-in thresholds
	 * @return a promise that will be completed when the TALK_DETECTION function had been called
	 */
	public CompletableFuture<Void> talkDetection(boolean enable) {
		return setVariable(enable ? "TALK_DETECT(set)" : "TALK_DETECT(remove)", "");
	}
	
	/**
	 * Enable talk detection using TALK_DETECTION function and set the detection thresholds
	 * @param talkMS talking detection threshold in milliseconds
	 * @param silenceMS silence detection threshold in milliseconds
	 * @return a promise that will be completed when the TALK_DETECTION function had been called
	 */
	public CompletableFuture<Void> talkDetection(int talkMS, int silenceMS) {
		return setVariable("TALK_DETECT(set)", silenceMS + "," + talkMS);
	}

	/**
	 * Get the channel object for the current caller channel
	 * @return channel that is currently running the stasis application
	 */
	public AsteriskChannel getChannel() {
		return new AsteriskChannel(getARIty(), callState);
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
	public Mute mute(AsteriskChannel.Mute direction) {
		return new Mute(this, direction);
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
	 * Check if the current call's channel is still available in Asterisk.
	 * A faster check might be to call {@link CallState#isActive()} as that gets updated automatically when Asterisk
	 * reports that a channel was disconnected.
	 * @return a promise that will resolve to <code>true</code> if the channel is still in Asterisk, <code>false</code>
	 * otherwise
	 */
	public CompletableFuture<Boolean> isCallActive() {
		return callState.getArity().channels().get(getChannelId())
				.thenApply(result -> {
					logger.info(logmarker, "Call with id: " + result.getId() + " is still active");
					return true;
				})
				.exceptionally(Futures.on(RestException.class, e -> {
					logger.info(logmarker, "Call is not active ");
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
	
	/**
	 * Helper to capture a single event on the channel for this call controller
	 * @param <T> Type of ARI message to listen for
	 * @param type Type of ARI message to listen for
	 * @param eventHandler handler for the first message received on this channel, since starting to listen
	 * @return the event handler created, which can be used to cancel the registration
	 */
	public <T extends Message> EventHandler<T> listenForOneTimeEvent(Class<T> type, Consumer<T> eventHandler) {
		return getARIty().listenForOneTimeEvent(type, getChannelId(), eventHandler);
	}
	
	/**
	 * Helper to capture events on the channel for this call controller
	 * @param <T> Type of ARI message to listen for
	 * @param type Type of ARI message to listen for
	 * @param eventHandler handler for receiving messages of the specified type sent on the channel
	 * @return the event handler created, which can be used to cancel the registration
	 */
	public <T extends Message> EventHandler<T> listenForEvent(Class<T> type, BiConsumer<T,EventHandler<T>> eventHandler) {
		return getARIty().addEventHandler(type, getChannelId(), eventHandler);
	}
}

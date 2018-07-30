package io.cloudonix.arity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.generated.Variable;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ErrorStream;
import io.cloudonix.future.helper.FutureHelper;

/**
 * The class represents a call controller, including all the call operation and
 * needed information for a call
 * 
 * @author naamag
 *
 */
public abstract class CallController {

	private CallState callState;
	private CallMointor callMonitor;
	private Logger logger = Logger.getLogger(getClass().getName());
	private List<Conference> conferences = null;

	/**
	 * Initialize the callController with the needed fields
	 * 
	 * @param stasisStartEvent
	 *            StasisStart
	 * @param ari
	 *            ARI
	 * @param arity
	 *            ARIty
	 */
	public void init(StasisStart stasisStartEvent, ARI ari, ARIty arity) {
		callState = new CallState(stasisStartEvent, ari, arity, stasisStartEvent.getChannel().getId(), stasisStartEvent.getChannel(),
				getChannelTechnology(stasisStartEvent.getChannel()));
		callMonitor = new CallMointor(arity, stasisStartEvent.getChannel().getId());
		callMonitor.monitorCallHangUp();
		logger = Logger.getLogger(getClass().getName() + ":" + stasisStartEvent.getChannel().getId());
	}

	/**
	 * get the StasisStart from the call
	 * 
	 * @return
	 */
	public StasisStart getCallStasisStart() {
		return callState.getCallStasisStart();
	}

	/**
	 * get the ari from the call
	 * 
	 * @return
	 */
	public ARI getAri() {
		return callState.getAri();
	}

	/**
	 * get the service from the call
	 * 
	 * @return
	 */
	public ARIty getARItyService() {
		return callState.getArity();
	}

	/**
	 * get the channel id of the call
	 * 
	 * @return
	 */
	public String getChannelID() {
		return callState.getChannelID();
	}

	/**
	 * play media to a channel
	 * 
	 * @param file
	 *            file to be played
	 * @return
	 */
	public Play play(String file) {
		return new Play(this, file);
	}

	/**
	 * play stored recording to a channel
	 * 
	 * @param recName
	 *            recording name to play
	 * @return
	 */
	public Play playRecording(String recName) {
		Play playRecording = new Play(this, recName);
		playRecording.playRecording();
		return playRecording;
	}

	/**
	 * the method creates a new Answer operation to answer the call
	 * 
	 * @return
	 */
	public Answer answer() {
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
	 * @param channelId
	 *            the channel id that wants ringing indication
	 * @return
	 */
	public Ring ring(String channelId) {
		return new Ring(this, channelId);
	}

	/**
	 * the method creates a new receivedDTMF object
	 * 
	 * @param terminatingKey
	 * @param inputLenght
	 * @param maxDuration
	 * @return
	 */
	public ReceivedDTMF receivedDTMF(String terminatingKey, int inputLenght, int maxDuration) {
		return new ReceivedDTMF(this, terminatingKey, inputLenght, maxDuration);
	}

	/**
	 * the method creates a new receivedDTMF operation with default values
	 * 
	 * @return
	 */
	public ReceivedDTMF receivedDTMF() {
		return new ReceivedDTMF(this);
	}

	/**
	 * the method created new Dial operation
	 *
	 * @param number
	 *            destination number
	 * @return
	 */
	public Dial dial(String number, String callerId) {
		return new Dial(this, callerId, number);
	}

	/**
	 * the method created new Dial operation
	 *
	 * @param number
	 *            destination number
	 * @param callerId
	 *            id of the caller
	 * @param headers
	 *            headers to add to the other channel we are dialing to
	 * @param timeout
	 *            the time we wait until the callee to answer the call
	 * @return
	 */
	public Dial dial(String number, String callerId, Map<String, String> headers, int timeout) {
		return new Dial(this, callerId, number, headers, timeout);
	}

	/**
	 * the method creates a new Conference
	 * 
	 * @param confName
	 *            name of the conference
	 * @return
	 */
	public Conference conference(String confName) {
		return new Conference(this, confName);
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
				.thenCompose(v -> Objects.nonNull(th) ? FutureHelper.completedExceptionally(th)
						: CompletableFuture.completedFuture(value));
	}

	/**
	 * set channel variable
	 * 
	 * @param channelId
	 *            id of the channel we want to set the variable to
	 * @param varName
	 *            name of the variable
	 * @param varValue
	 *            value of the variable
	 * @return
	 */
	public CompletableFuture<Void> setChannelVariable(String channelId, String varName, String varValue) {
		return this
				.<Void>futureFromAriCallBack(
						cb -> callState.getAri().channels().setChannelVar(channelId, varName, varValue, cb))
				.exceptionally(t -> {
					logger.fine("Unable to set variable: " + varName);
					return null;
				});
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
		return this.<Variable>futureFromAriCallBack(cb -> callState.getAri().channels()
				.getChannelVar(callState.getChannelID(), "SIP_HEADER(" + headerName + ")", cb)).thenApply(v -> {
					return v.getValue();
				}).exceptionally(t -> {
					logger.fine("Unable to find header: " + headerName);
					return null;
				});
	}

	/**
	 * get the value of a specific PJSIP header
	 * 
	 * @param headerName
	 *            the name of the header, for example: To
	 * @return
	 * @throws RestException
	 */
	public CompletableFuture<String> getPJSipHeader(String headerName) {
		return this
				.<Variable>futureFromAriCallBack(cb -> callState.getAri().channels()
						.getChannelVar(callState.getChannelID(), "PJSIP_HEADER(" + headerName + ")", cb))
				.thenApply(v -> {
					return v.getValue();
				}).exceptionally(t -> {
					logger.fine("Unable to find header: " + headerName);
					return null;
				});
	}

	/**
	 * add sip header to a channel
	 * 
	 * @param headerName
	 *            name of the new header
	 * @param headerValue
	 *            value of the new header
	 * @return
	 */
	public CompletableFuture<Void> setSipHeader(String headerName, String headerValue) {
		return this.<Void>futureFromAriCallBack(cb -> callState.getAri().channels()
				.setChannelVar(callState.getChannelID(), "SIP_HEADER(" + headerName + ")", headerValue, cb))
				.exceptionally(t -> {
					logger.fine("Unable to find header: " + headerName);
					return null;
				});
	}

	/**
	 * add pjsip header to a channel
	 * 
	 * @param headerName
	 *            name of the new header
	 * @param headerValue
	 *            value of the new header
	 * @return
	 */
	public CompletableFuture<Void> setPJSipHeader(String headerName, String headerValue) {
		return this.<Void>futureFromAriCallBack(cb -> callState.getAri().channels()
				.setChannelVar(callState.getChannelID(), "PJSIP_HEADER(" + headerName + ")", headerValue, cb))
				.exceptionally(t -> {
					logger.fine("Unable to find header: " + headerName);
					return null;
				});
	}

	/**
	 * get the value of a channel variable
	 * 
	 * @param varName
	 *            name of the channel variable we are asking for
	 * @return
	 */
	public CompletableFuture<String> getVariable(String varName) {
		return this
				.<Variable>futureFromAriCallBack(
						cb -> callState.getAri().channels().getChannelVar(callState.getChannelID(), varName, cb))
				.thenApply(v -> {
					return v.getValue();
				}).exceptionally(t -> {
					logger.fine("Unable to find variable: " + varName + " :" + ErrorStream.fromThrowable(t));
					return null;
				});
	}

	/**
	 * change setting regarding to TALK_DETECT function
	 * 
	 * @param action
	 *            'set' or 'remove'
	 * @param actionValue
	 *            if set action is used, action value will be in the form:
	 *            'threshold1,threshold2' such that threshold 1 is the time in
	 *            milliseconds before which a user is considered silent. and
	 *            threshold 2 is the time in milliseconds after which a user is
	 *            considered talking. use the empty string for no threshold
	 * @return
	 */
	public CompletableFuture<Void> setTalkingInChannel(String action, String actionValue) {
		return this.<Void>futureFromAriCallBack(cb -> callState.getAri().channels()
				.setChannelVar(callState.getChannelID(), "TALK_DETECT(" + action + ")", actionValue, cb))
				.exceptionally(t -> {
					logger.info(
							"Unable to " + action + " with value " + actionValue + ": " + ErrorStream.fromThrowable(t));
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
				logger.info("Conference with name: " + name + " exists");
				return true;
			}
		}
		logger.info("No conference with name: " + name);
		return false;
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
		return callState.getChannel().getDialplan().getExten();
	}

	/**
	 * return account code of the channel (information about the channel)
	 * 
	 * @return
	 */
	public String getAccountCode() {
		return callState.getChannel().getAccountcode();
	}

	/**
	 * get the caller (whom is calling)
	 * 
	 * @return
	 */
	public String getCallerIdNumber() {
		return callState.getChannel().getCaller().getNumber();
	}

	/**
	 * get the name of the channel (for example: SIP/myapp-000001)
	 * 
	 * @return
	 */
	public String getChannelName() {
		return callState.getChannel().getName();
	}

	/**
	 * return channel state
	 * 
	 * @return
	 */
	public String getChannelState() {
		return callState.getChannel().getState();
	}

	/**
	 * get channel creation time
	 * 
	 * @return
	 */
	public String getChannelCreationTime() {
		return callState.getChannel().getCreationtime().toString();
	}

	/**
	 * return dialplan context (for example: ari-context)
	 * 
	 * @return
	 */
	public String getDialplanContext() {
		return callState.getChannel().getDialplan().getContext();
	}

	/**
	 * get the dialplan priority
	 * 
	 * @return
	 */
	public long getPriority() {
		return callState.getChannel().getDialplan().getPriority();
	}

	/**
	 * get the channel technology (ex: SIP or PJSIP)
	 * 
	 * @param channel
	 * @return
	 */
	private String getChannelTechnology(Channel channel) {
		String chanName = channel.getName();
		String[] technology = chanName.split("/");
		return technology[0];
	}

	/**
	 * add data about the call
	 * 
	 * @param dataName
	 *            name of the data we are adding
	 * @param dataContent
	 *            object that contains the content of the data
	 */
	public void put(String dataName, Object dataContent) {
		callState.put(dataName, dataContent);
	}

	/**
	 * get data about the call
	 * 
	 * @param dataName
	 *            name of the data we asking for
	 */
	public <T> T get(String dataName) {
		return callState.get(dataName);
	}

	/**
	 * get callState of the current CallContorller
	 * 
	 * @return
	 */
	public CallState getCallState() {
		return callState;
	}

	/**
	 * create new record operation
	 * 
	 * @param name
	 *            Recording's filename
	 * @param fileFormat
	 *            Format to encode audio in (wav, gsm..)
	 * @return
	 */
	public Record record(String name, String format) {
		return new Record(this, name, format);
	}

	/**
	 * create record operation with more settings
	 * 
	 * @param callController
	 *            instant representing the call
	 * @param name
	 *            Recording's filename
	 * @param fileFormat
	 *            Format to encode audio in (wav, gsm..)
	 * @param maxDuration
	 *            Maximum duration of the recording, in seconds. 0 for no limit
	 * @param maxSilenceSeconds
	 *            Maximum duration of silence, in seconds. 0 for no limit
	 * @param beep
	 *            true if we want to play beep when recording begins, false
	 *            otherwise
	 * @param terminateOnKey
	 *            DTMF input to terminate recording (allowed values: none, any, *,
	 *            #)
	 * @return
	 */
	public Record record(String name, String format, int maxDuration, int maxSilence, boolean beep, String termKey) {
		return new Record(this, name, format, maxDuration, maxSilence, beep, termKey);
	}

	/**
	 * transfer CallController to the next CallController
	 * 
	 * @param nextCallController
	 *            CallController that we are getting the data from
	 */
	public CompletableFuture<Void> execute(CallController nextCallController) {
		nextCallController.callState = callState;
		nextCallController.callMonitor = callMonitor;
		nextCallController.conferences = conferences;
		return nextCallController.run();
	}

	/**
	 * if the channel is still active return true, false otherwise
	 * 
	 * @param channelId
	 *            channel id of the call to be checked
	 * @return
	 * @throws RestException 
	 */
	public boolean isCallActive(String channelId){
		
		/*CompletableFuture<Boolean> future = new CompletableFuture<Boolean> ();
		
		callState.getAri().channels().get(channelId, new AriCallback<Channel>() {

			@Override
			public void onSuccess(Channel result) {
				logger.info("Call with id: "+ result.getId()+ " is still active");
				future.complete(true);
			}

			@Override
			public void onFailure(RestException e) {
				logger.info("Call is not active ");
				future.complete(false);
			}
		});
		return future.thenApply(isActive->isActive);*/
		
		try {
			if (Objects.nonNull(callState.getAri().channels())) {
				callState.getAri().channels().get(channelId);
				logger.info("Call with id: " + channelId + " is still active");
				return true;
			}
			else {
				logger.info("No channels exists");
				return false;
			}
		} catch (RestException e) {
			logger.info("Call with id: " + channelId + " is not active: " + e);
			return false;
		}
	}
	
	/**
	 * what to do if call controller hanged up (the caller's channel)
	 */
	public void onHangUp(){}

	public abstract CompletableFuture<Void> run();
}

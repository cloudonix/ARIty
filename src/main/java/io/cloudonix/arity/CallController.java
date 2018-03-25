package io.cloudonix.arity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
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

	private StasisStart callStasisStart;
	private ARI ari;
	private String channelID;
	private ARIty arity;
	// sip headers that were added by request, not part of the existing headers
	private Map<String, String> addedSipHeaders = null;

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

		callStasisStart = ss;
		channelID = ss.getChannel().getId();
		ari = a;
		this.arity = ARIty;
		addedSipHeaders = new HashMap<String, String>();

	}

	/**
	 * get the StasisStart from the call
	 * 
	 * @return
	 */
	public StasisStart getCallStasisStart() {
		return callStasisStart;
	}

	/**
	 * get the ari from the call
	 * 
	 * @return
	 */
	public ARI getAri() {
		return ari;
	}

	/**
	 * get the service from the call
	 * 
	 * @return
	 */
	public ARIty getARItyService() {
		return arity;
	}

	/**
	 * get the channel id of the call
	 * 
	 * @return
	 */
	public String getChannelID() {
		return channelID;
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
		return new Dial(this, number);
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
		
		return this.<Variable>futureFromAriCallBack(cb -> ari.channels().getChannelVar(channelID, "SIP_HEADER(" + headerName + ")", cb))
				.thenApply(v -> {
					return v.getValue();
				});

	}

	/**
	 * helper method for getSipHeader in order to have AriCallback
	 * @param consumer
	 * @return
	 */
	private <V> CompletableFuture<V> futureFromAriCallBack(Consumer<AriCallback<V>> consumer) {
		CompletableFuture<V> compFuture = new CompletableFuture<V>();

		consumer.accept(new AriCallback<V>() {

			@Override
			public void onSuccess(V result) {
				compFuture.complete(result);
			}

			@Override
			public void onFailure(RestException e) {
				compFuture.completeExceptionally(e);

			}
		});
		return compFuture;
	}

	/**
	 * add a sip header
	 * 
	 * @param headerName the name of the header, for example: (To)
	 * @param headerValue the value of the header
	 * @throws RestException
	 */
	public void addSipHeader(String headerName, String headerValue) throws RestException {
		// ari.channels().getChannelVar(channelID, headerName).setValue(headerValue);
		addedSipHeaders.put("SIP_HEADER(" + headerName + ")", headerValue);
	}

}

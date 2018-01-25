package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.StasisStart;

/**
 * The class represents an incoming call
 * 
 * @author naamag
 *
 */
public class Call {

	private StasisStart callStasisStart;
	private ARI ari;
	private String channelID;
	private ARIty arity;

	/**
	 * constructor 
	 * @param ss Stasis start event
	 * @param a ARI instant
	 * @param ARIty the ARIty service
	 */
	public Call(StasisStart ss, ARI a, ARIty ARIty) {

		callStasisStart = ss;
		channelID = ss.getChannel().getId();
		ari = a;
		this.arity = ARIty;
	}

	/**
	 * The method executes the voice application
	 * 
	 * @param voiceApp the voice application
	 */
	public void executeVoiceApp(Runnable voiceApp) {
		voiceApp.run();
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
	 * @param file file to be played
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
	 * @param number the number of the endpoint (who are we calling to)
	 * @return
	 */
	public Operation dial(String number) {
		return new Dial(this, number);
	}

	/**
	 * the method verifies that the call is always hangs up, even if an error occurred
	 * during any operation
	 * 
	 * @param value if no error occurred
	 * @param th if an error occurred it will contain the error
	 * @return
	 */
	public <V> CompletableFuture<V> endCall(V value, Throwable th) {
		return hangup().run()
				.handle((hangup, th2) -> null)
				.thenCompose(v -> Objects.nonNull(th) ? Operation.completedExceptionally(th)
						: CompletableFuture.completedFuture(value));
	}

}

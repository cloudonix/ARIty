package io.cloudonix.arity;

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

	public Call(StasisStart ss, ARI a, ARIty aRIty) {

		callStasisStart = ss;
		channelID = ss.getChannel().getId();
		ari = a;
		this.arity = aRIty;
	}

	/**
	 * The method executes the application
	 * 
	 * @param voiceApp
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
	public ARIty getService() {
		return arity;
	}

	/**
	 * get the channel id from the call
	 * 
	 * @return
	 */
	public String getChannelID() {
		return channelID;
	}

	/**
	 * The method creates a new Play object with the file to be played
	 * 
	 * @param file
	 * @return Play
	 */
	public Play play(String file) {
		return new Play(this, file);
	}

	/**
	 * the method creates a new Answer object to answer the call
	 * @return Answer
	 */
	public Answer answer() {
		return new Answer(this);
	}

	/**
	 * the method creates a new HangUp object to hang up the call
	 * @return HangUp
	 */
	public HangUp hangUp() {
		return new HangUp(this);
	}
	
	/**
	 * the method creates a new Gather object 
	 * @return Gather
	 */
	public Gather gather (String terminatingKey) {
		return new Gather(this,terminatingKey );
	}
	
	/**
	 * the method creates a new Gather object 
	 * @return Gather
	 */
	public Gather gather () {
		return new Gather(this);
	}

}

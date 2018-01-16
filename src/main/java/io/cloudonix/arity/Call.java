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
	private ARIty aRIty;

	public Call(StasisStart ss, ARI a, ARIty aRIty) {

		callStasisStart = ss;
		channelID = ss.getChannel().getId();
		ari = a;
		this.aRIty = aRIty;
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
	 * @return
	 */
	public  StasisStart getCallStasisStart () {
		return callStasisStart;
	}
	
	/**
	 * 	get the ari from the call
	 * @return
	 */
	public  ARI getAri () {
		return ari;
	}
	
	/**
	 * get the service from the call
	 * @return
	 */
	public ARIty getService () {
		return aRIty;
	}
	
	/**
	 * get the channel id from the call
	 * @return
	 */
	public String getChannelID () {
		return channelID;
	}
	
}

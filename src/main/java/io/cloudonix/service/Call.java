package io.cloudonix.service;

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
	private Service service;

	public Call(StasisStart ss, ARI a, Service service) {

		callStasisStart = ss;
		channelID = ss.getChannel().getId();
		ari = a;
		this.service = service;
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
	public Service getService () {
		return service;
	}
	
	/**
	 * get the channel id from the call
	 * @return
	 */
	public String getChannelID () {
		return channelID;
	}
	
}

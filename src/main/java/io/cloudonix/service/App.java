package io.cloudonix.service;

import java.net.URISyntaxException;
import java.util.logging.Logger;

import io.cloudonix.service.Service;
import io.cloudonix.service.errors.AnswerCallException;
import io.cloudonix.service.errors.ConnectionFailedException;
import io.cloudonix.service.errors.HangUpException;

public class App {

	private final static Logger logger = Logger.getLogger(App.class.getName());

	private static String URI = "http://127.0.0.1:8088/";

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		App app = new App();
		// Create the service of ARI
		Service ari = null;
		try {
			// dial case
			ari = new Service(URI, "stasisApp", "userid", "secret", "dial");
			// call case
			//ari = new Service(URI, "stasisApp", "userid", "secret", "call");
			
		} catch (Throwable e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		ari.registerDialApp(app::dialApp);
	//	ari.registerVoiceApp(app::voiceAPP);

		while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Thread is not sleeping");
			}
		}
	}

	/**
	 * The method represent the continuity of a call scenario
	 * 
	 * @param c
	 * @throws AnswerCallException
	 */
	public void voiceAPP(Call c) {
		c.answer()
		/*
		 * //then play record (assuming there is "myRecording")
		.thenCompose(recfin -> c.playRecording("myRecording"))
		.thenAccept(rf -> logger.info("finished playing record"))
		*/
				// then play sound
				.thenCompose(v -> c.playSound(3, "hello-world")).thenCompose(pb -> {
					logger.info("finished playback! id: " + pb.getId());
					return c.hangUpCall();
				}).thenAccept(h -> {
					logger.info("hanged up call");
				}).exceptionally(t -> {
					logger.severe(t.toString());
					return null;
				});

	}
	
	public void dialApp (DialCall d) {
		
		d.DialSIP("sip:app2@172.25.0.6")
		.thenAccept(v -> {
			logger.info("the other side recieved the call");
		}).exceptionally(t -> {
			logger.severe(t.toString());
			return null;
		});		
		
	}
}

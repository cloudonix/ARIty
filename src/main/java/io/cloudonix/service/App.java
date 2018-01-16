package io.cloudonix.service;

import java.net.URISyntaxException;
import java.util.logging.Logger;

import io.cloudonix.service.ARIty;
import io.cloudonix.service.errors.AnswerCallException;
import io.cloudonix.service.errors.ConnectionFailedException;

public class App {

	private final static Logger logger = Logger.getLogger(App.class.getName());

	private static String URI = "http://127.0.0.1:8088/";

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		App app = new App();
		// Create the service of ARI
		ARIty ari = null;
		try {
			ari = new ARIty(URI, "stasisApp", "userid", "secret");

		} catch (Throwable e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// ari.registerVoiceApp(app::voiceApp3);
		ari.registerVoiceApp(app::voiceApp2);

		while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Thread is not sleeping");
			}
		}
	}

	/**
	 * The application represent the continuity of a call scenario
	 * 
	 * @param c
	 * @throws AnswerCallException
	 *//*
	public void voiceAPP(Call c) {
		c.answer()
				// then play sound
				.thenCompose(v -> c.playSound(2, "hello-world"))
				.thenAccept(pb -> logger.info("finished playback! id: " + pb.getId()))
				.thenCompose(g -> c.gatherInput("#")).thenCompose(d -> {
					logger.info("gather is finished");
					return c.hangUpCall();
				}).thenAccept(h -> {
					logger.info("hanged up call");
				}).exceptionally(t -> {
					logger.severe(t.toString());
					return null;
				});

		
		 * //then play record (assuming there is "myRecording") .thenCompose(recfin ->
		 * c.playRecording("myRecording")) .thenAccept(rf ->
		 * logger.info("finished playing record"))
		 

	}

	// dial application to sip
	public void voiceApp2(Call d) {

		d.DialSIP("SIP/app2").thenAccept(v -> {
			logger.info("the other side recieved the call");
		}).exceptionally(t -> {
			logger.severe(t.toString());
			return null;
		});

	}*/
	
	public void voiceApp2(Call call) {
		
		new Dial(call).run("SIP/app2")
		.thenAccept(v->{logger.info("the other side recieved the call");
		}).exceptionally(t -> {
			logger.severe(t.toString());
			return null;
		});
	}
	

	public void voiceApp3 (Call call) {
		new Answer(call).run()
		.thenCompose(v-> new Play(call).runSound(2, "hello-world"))
		.thenAccept(pb->logger.info("finished playback! id: " + pb.getId()))
		.thenCompose(g-> new Gather(call).run("#"))
		.thenCompose(d -> {
			logger.info("gather is finished");
			return new HangUp(call).run();
		}).thenAccept(h -> {
			logger.info("hanged up call");
		}).exceptionally(t -> {
			logger.severe(t.toString());
			return null;
		});
	}
	
}

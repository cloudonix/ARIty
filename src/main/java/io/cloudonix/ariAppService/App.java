package io.cloudonix.ariAppService;

import java.net.URISyntaxException;
import java.util.logging.Logger;

import io.cloudonix.ariAppService.Service;
import io.cloudonix.logicException.ConnectionFailedException;
import io.cloudonix.logicException.HangUpException;

public class App {

	private final static Logger logger = Logger.getLogger(App.class.getName());

	private static String URI = "http://127.0.0.1:8088/";

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		App app = new App();
		//Create the service of ARI
		Service ari = null;
		try {
			ari = new Service(URI, "stasisApp", "userid", "secret");
		} catch (Throwable e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		ari.registerVoiceApp(app::voiceAPP);
		
		while(true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Thread is not sleeping");
			}
		}
	}
	
	/**
	 * The method represent the continuity of a call scenario
	 * @param c
	 */
	public void voiceAPP(Call c) {
		// answer the call
		c.answer()
				// then play
				.thenCompose(v -> c.play(3)).thenCompose(pb -> {
					logger.info("finished playback! id: " + pb.getId());
					// hang up the call
					try {
						return c.hangUpCall();
					} catch (HangUpException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return null;
				}).thenAccept(h -> {
					logger.info("hanged up call");
				}).exceptionally(t -> {
					logger.severe(t.toString());
					return null;
				});

	}
}

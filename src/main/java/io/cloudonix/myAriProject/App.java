package io.cloudonix.myAriProject;

import java.util.logging.Logger;
import io.cloudonix.myAriProject.Service;

public class App {

	private final static Logger logger = Logger.getLogger(App.class.getName());

	private static String URI = "http://127.0.0.1:8088/";

	public static void main(String[] args) {
		App app = new App();
		Service ari = new Service(URI, "stasisApp", "userid", "secret");
		ari.registerVoiceApp(app::voiceAPP);
	}

	public void voiceAPP(Call c) {
		// answer the call
		c.answer()
				// then play
				.thenCompose(v -> c.play(2)).thenCompose(pb -> {
					logger.info("finished playback! id: " + pb.getId());
					// hang up the call
					return c.hangUpCall();
				}).thenAccept(h -> {
					logger.info("hanged up call");
				}).exceptionally(t -> {
					logger.severe(t.toString());
					return null;
				});

	}
	/*
	 * private static void voiceApp(ARIfunctionality myARI) { // answer the call
	 * myARI.answer() // then play .thenCompose(v -> myARI.play(2)).thenCompose(pb
	 * -> { logger.info("finished playback! id: " + pb.getId()); // hang up the call
	 * return myARI.hangUpCall(); }).thenAccept(h -> {
	 * logger.info("hanged up call"); }).exceptionally(t -> {
	 * logger.severe(t.toString()); return null; });
	 */

	// }
}

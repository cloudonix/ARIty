package io.cloudonix.samples;

import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallController;
import io.cloudonix.arity.Play;
import io.cloudonix.arity.errors.ConnectionFailedException;

/**
 * Sample for playing "conf-adminmenu-18" (menu options) and cancelling it after
 * 5 seconds
 * 
 * @author naamag
 *
 */
public class CancelOperationSample extends CallController {

	private final static Logger logger = Logger.getLogger(CancelOperationSample.class.getName());

	@Override
	public CompletableFuture<Void> run() {
		return CompletableFuture.completedFuture(null);
	}

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		logger.info("websocket is connected to: " + arity.getConnetion());

		// lambda case
		arity.registerVoiceApp(call -> {
			call.answer().run().thenCompose(v -> {
				Play play = call.play("conf-adminmenu-18");
				startTimer(play);
				return play.run();
			}).thenAccept(p -> logger.info("Finished playback! id: " + p.getPlayback().getId()))
			.handle(call::endCall)
			.exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});
		});

		while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Thread is not sleeping");
			}
		}
	}

	/**
	 * start a timer that will cancel playing the play back
	 * 
	 * @param play play instance
	 */
	private static void startTimer(Play play) {
		Timer timer = new Timer("Timer");
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				logger.info("Canceling playback!!");
				play.cancel();
			}
		};
		timer.schedule(task, TimeUnit.SECONDS.toMillis(5));

	}
}

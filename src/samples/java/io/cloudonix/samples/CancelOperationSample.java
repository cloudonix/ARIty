package io.cloudonix.samples;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallController;
import io.cloudonix.arity.errors.ConnectionFailedException;

/**
 * Sample for playing "dictate/play_help" (menu options),"hello" and "goodbye".
 * Stop playing when done receiving DTMF from the caller (can be while
 * "dictate/play_help" or "hello" or "goodbye" is playing or after all of them
 * finished playing). Then before hanging up the call, play "thank-you" to the caller
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
			call.answer().run()
					.thenCompose(anserRes -> call.receivedDTMF().and(call.play("dictate/play_help"))
							.and(call.play("hello")).and(call.play("goodbye")).run())
					.thenCompose(dtmf -> call.play("auth-thankyou").loop(2).run())
					.thenAccept(playRes -> logger.info("Done receiving DTMF and playing")).handle(call::endCall)
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
}

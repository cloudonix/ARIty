package io.cloudonix.samples;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallController;
import io.cloudonix.arity.errors.ConnectionFailedException;
/**
 * Sample for playing "hello-world" and stop playing it (if not finished to play it 7 times) when done receiving DTMF from the caller.
 * Then before hanging up the call, play "goodbye" to the caller
 * 
 * @author naamag
 *
 */
public class CancelOperationSample  extends CallController{

	private final static Logger logger = Logger.getLogger(AnswerAndPlay.class.getName());

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
			 .thenCompose(anserRes -> call.receivedDTMF().and(call.play("hello-world").loop(7)).run())
			 .thenCompose(dtmf -> call.play("goodbye").loop(2).run())
			 .thenAccept(playRes ->logger.info("Done receiving DTMF and playing"))
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
}

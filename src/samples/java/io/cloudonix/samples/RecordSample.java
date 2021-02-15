package io.cloudonix.samples;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallController;
import io.cloudonix.arity.errors.ConnectionFailedException;
/**
 * Sample for recording and playing the recording after finished recording
 * @author naamag
 *
 */
public class RecordSample extends CallController {
	private final static Logger logger = LoggerFactory.getLogger(AnswerAndPlay.class);

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
			  .thenCompose(v-> call.record("test2", "wav",10, 0,true,"*").run()) 
			  .thenCompose(res->call.play("test2").playRecording())
			  .handle(call::endCall) 
			  .exceptionally(t -> {
					logger.error("Error ending call", t);
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

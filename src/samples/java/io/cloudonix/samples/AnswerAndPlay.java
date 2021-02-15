package io.cloudonix.samples;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallController;
import io.cloudonix.arity.errors.ConnectionFailedException;

/**
 * Sample for answering the call and playing "hello-world" 3 times
 * 
 * @author naamag
 *
 */
public class AnswerAndPlay extends CallController {
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
			call.answer().run().thenCompose(v -> call.play("hello-world").loop(3).run())
			.thenCompose(pb -> {
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return call.hangup().run();
			}).handle(call::endCall).exceptionally(t -> {
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

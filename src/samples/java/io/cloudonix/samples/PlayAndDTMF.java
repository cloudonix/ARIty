package io.cloudonix.samples;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallController;
import io.cloudonix.arity.ReceiveDTMF;
import io.cloudonix.arity.errors.ConnectionFailedException;

/**
 * Sample of answering the call, playing "followme/options" and then wait for
 * the caller to press 1 or 2 and then * to finish (by default, to terminate
 * receiving DTMF the caller should press #)
 * 
 * @author naamag
 *
 */
public class PlayAndDTMF extends CallController {
	private final static Logger logger = LoggerFactory.getLogger(PlayAndDTMF.class);

	@Override
	public CompletableFuture<Void> run() {
		return CompletableFuture.completedFuture(null);
	}

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		logger.info("websocket is connected to: " + arity.getConnetion());

		// lambda case
		arity.registerVoiceApp(call -> {
			// playback can be played more then once. if playing once no need to use loop()
			call.answer().run()
					.thenCompose(v -> call.play("followme/options").loop(1).run()) 
					.thenAccept(pb -> logger.info("Finished playback! id: " + pb.getPlayback().getId()))
					.thenCompose(g -> call.receiveDTMF("*", 2).run())
					.thenAccept(v -> logger
							.info("RecievedDTMF is finished! The input is: " + ((ReceiveDTMF) v).getInput()))
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

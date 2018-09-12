package io.cloudonix.samples;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallController;
import io.cloudonix.arity.ReceivedDTMF;
import io.cloudonix.arity.errors.ConnectionFailedException;

/**
 * Sample of answering the call, playing "hello-world" and then wait for receiving DTMF from the caller (by default, to terminate receiving 
 * DTMF the caller should press #) 
 * @author naamag
 *
 */
public class PlayAndDTMF extends CallController{
	private final static Logger logger = Logger.getLogger(PlayAndDTMF.class.getName());

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
		 	.thenCompose(v ->
		 		call.play("hello-world").loop(2).run()) 
		 		.thenAccept(pb -> logger.info("finished playback! id: " + pb.getPlayback().getId()))
		 		.thenCompose(g -> call.receivedDTMF().run())
		 		.thenAccept(v -> logger.info("RecievedDTMF is finished! The input is: " + ((ReceivedDTMF)v).getInput()))
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

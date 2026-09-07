package io.cloudonix.samples;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.ARItyOptions;
import io.cloudonix.arity.CallController;
import io.cloudonix.arity.errors.ConnectionFailedException;
import io.vertx.core.Vertx;

/**
 * Sample for dial to another sip
 * 
 * @author naamag
 *
 */
public class DialSample extends CallController {

	private final static Logger logger = LoggerFactory.getLogger(DialSample.class);

	@Override
	public CompletableFuture<Void> run() {
		return CompletableFuture.completedFuture(null);
	}

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		ARIty arity = ARIty.create(Vertx.vertx(), new ARItyOptions().uri("http://127.0.0.1:8088/").appName("stasisApp").login("userid").password("secret"));
		logger.info("websocket is connected to: " + arity.getConnetion());

		// lambda case
		arity.registerVoiceApp(call -> {
			call.dial("myCallerId", "SIP/123").run()
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

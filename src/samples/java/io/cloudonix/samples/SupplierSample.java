package io.cloudonix.samples;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallController;
import io.cloudonix.arity.errors.ConnectionFailedException;

public class SupplierSample extends CallController {
	private final static Logger logger = Logger.getLogger(SupplierSample.class.getName());

	@Override
	public CompletableFuture<Void> run() {
		return CompletableFuture.completedFuture(null);
	}

	public void voiceApp(CallController call) {
		call.answer().run().thenCompose(v -> call.play("hello-world").loop(2).run()).thenCompose(pb -> {
			logger.info("finished playback! id: " + pb.getPlayback().getId());
			return call.hangup().run();
		}).handle(call::endCall).exceptionally(t -> {
			logger.severe(t.toString());
			return null;
		});
	}

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		SupplierSample app = new SupplierSample();

		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		logger.info("websocket is connected to: " + arity.getConnetion());

		arity.registerVoiceApp(app::voiceApp);
		
		while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Thread is not sleeping");
			}
		}
	}
}

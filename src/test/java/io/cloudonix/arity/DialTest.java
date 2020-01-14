package io.cloudonix.arity;


import java.util.concurrent.CompletableFuture;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DialTest {

	public class Application extends CallController {

		@Override
		public CompletableFuture<Void> run() {
			// call scenario - voice application
			/*return answer().run().thenCompose(v -> play("hello-world").loop(2).run()).thenCompose(pb -> {
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return hangup().run();
			}).handle(this::endCall).exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});*/
			return CompletableFuture.completedFuture(null);
		}
	}

	static AsteriskContainer asterisk = new AsteriskContainer();

	@BeforeClass
	static public void beforeTesting() {
		asterisk.start();
	}

	@Test
	public void testRun() {

	}


	@AfterClass
	static public void afterTesting() {
		asterisk.stop();
	}

}

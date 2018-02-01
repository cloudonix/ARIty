package io.cloudonix.arity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DialTest {
	
	public class Application extends CallController {

		@Override
		public void run() {
			// call scenario - voice application
			answer().run().thenCompose(v -> play("hello-world").loop(2).run()).thenCompose(pb -> {
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return hangup().run();
			}).handle(this::endCall).exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});
		}
	}

	static AsteriskContainer asterisk = new AsteriskContainer();

	private final static Logger logger = Logger.getLogger(DialTest.class.getName());

	@BeforeAll
	static void beforeTesting() {
		asterisk.start();
	}
	
	@Test
	void testRun() {
		
	}
	
	
	@AfterAll
	static void afterTesting() {
		asterisk.stop();
	}

}

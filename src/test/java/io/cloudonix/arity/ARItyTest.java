package io.cloudonix.arity;

import static org.junit.Assert.*;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import io.cloudonix.ARItySipInitiator;
import io.cloudonix.arity.errors.ConnectionFailedException;
import io.cloudonix.arity.errors.InvalidCallStateException;
import io.cloudonix.test.support.AsteriskContainer;

public class ARItyTest {

	public static class Application extends CallController {
		public static boolean isSucceeded = false;

		@Override
		public CompletableFuture<Void> run() {
			isSucceeded = false;
			// call scenario - voice application
			return answer().run().thenCompose(v -> play("hello-world").loop(2).run()).thenCompose(pb -> {
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return hangup().run();
			}).handle(this::endCall).thenRun(() ->{
				isSucceeded = true;
			}).exceptionally(t -> {
				logger.error("Error ending call", t);
				return null;
			});

		}
	}

	@Rule
	public AsteriskContainer asterisk = new AsteriskContainer();
	
	@BeforeClass
	static public void setup() {
		ARItySipInitiator.class.getName(); // force class to init
	}

	private final static Logger logger = LoggerFactory.getLogger(ARItyTest.class);

	@Test(timeout = 15000)
	public void testConncetion() throws ConnectionFailedException, URISyntaxException, InterruptedException, ExecutionException {
		ARIty arity = asterisk.getARIty();
		arity.getActiveChannels().get();
	}

	@Test(timeout = 15000)
	// error while running an application
	public void testErrRun() throws Exception {
		AtomicBoolean wasCalled = new AtomicBoolean(false);
		logger.info("Starting testErrRun");
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		logger.info("Setup complete");
		arity.registerVoiceApp(call -> {
			logger.info("Call started");
			wasCalled.set(true);
			call.play("hello-world").loop(2).run();
			throw new RuntimeException("some error, this is on purpose for the test");
		});
		logger.info("Registered app, calling");
		int status = ARItySipInitiator.call(asterisk.getSipHostPort(), "127.0.0.1" ,"1234").get();
		logger.info("call done");
		arity.disconnect();
		assertTrue(wasCalled.get());
		assertEquals(603, status);
	}

	@Test(timeout = 30000, expected = InvalidCallStateException.class)
	//application is not registered
	public void testNotRegisteredApp() throws Exception {
		logger.info("Starting testNotRegisteredApp");
		Application app = new Application();
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		app.run();
		ARItySipInitiator.call(asterisk.getSipHostPort(), asterisk.getContainerIpAddress() ,"1234").get();
		arity.disconnect();
		logger.info("Ended testErrRun");
	}

}

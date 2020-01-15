package io.cloudonix.arity;

import static org.junit.Assert.*;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

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
				logger.severe(t.toString());
				return null;
			});

		}
	}

	public class LambdaApplication {
		public boolean isSucceeded = false;
		public void voiceApp(CallController call) {
			logger.info("voice application started");
			isSucceeded=false;
			call.answer().run().thenCompose(v -> call.play("hello-world").loop(2).run()).thenCompose(pb -> {
				logger.info("finished playback!");
				return call.hangup().run();
			}).handle(call::endCall).thenCompose(x->x)
			.thenRun(() ->{
				isSucceeded = true;
			}).exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});
		}
	}

	@Rule
	public AsteriskContainer asterisk = new AsteriskContainer();

	private final static Logger logger = Logger.getLogger(ARItyTest.class.getName());
	static {
		logger.setLevel(Level.ALL);
		ConsoleHandler console = new ConsoleHandler();
		console.setLevel(Level.ALL);
		logger.addHandler(console);
	}

	@Test(timeout = 30000)
	public void testConncetion() throws ConnectionFailedException, URISyntaxException, InterruptedException, ExecutionException {
		ARIty arity = asterisk.getARIty();
		arity.getActiveChannels().get();
		arity.disconnect();
	}

	@Test(timeout = 30000)
	// error while running an application
	public void testErrRun() throws Exception {
		AtomicBoolean wasCalled = new AtomicBoolean(false);
		logger.info("Starting testErrRun");
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		logger.info("Setup complete");
		arity.registerVoiceApp(call->{
			wasCalled.set(true);
			call.play("hello-world").loop(2).run();
			throw new RuntimeException("some error");
		});
		logger.info("Registered app, calling");
		ARItySipInitiator.call(asterisk.getSipHostPort(), "127.0.0.1" ,"1234").get();
		logger.info("Call done");
		arity.disconnect();
		assertTrue(wasCalled.get());
		logger.info("Ended testErrRun");
	}
	@Test(timeout = 30000, expected = InvalidCallStateException.class)
	//application is not registered
	public void testNotRegisteredApp() throws Exception {
		logger.info("Starting testErrRun");
		Application app = new Application();
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		app.run();
		ARItySipInitiator.call(asterisk.getSipHostPort(), asterisk.getContainerIpAddress() ,"1234").get();
		arity.disconnect();
		logger.info("Ended testErrRun");
	}

}

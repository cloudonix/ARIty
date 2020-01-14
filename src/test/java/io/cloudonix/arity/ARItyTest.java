package io.cloudonix.arity;

import static org.junit.Assert.*;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import io.cloudonix.ARItySipInitiator;
import io.cloudonix.arity.errors.ConnectionFailedException;
import io.cloudonix.arity.errors.InvalidCallStateException;

public class ARItyTest {

	public class Application extends CallController {
		public boolean isSucceeded = false;

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
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return call.hangup().run();
			}).handle(call::endCall)
			.thenRun(() ->{
				isSucceeded = true;
			}).exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});
		}
	}

	abstract class BrokenApp extends CallController {
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
	public void testARIty() throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.disconnect();
	}

	@Test(timeout = 30000)
	public void testRegisterSupplier() throws Exception{
		LambdaApplication app = new LambdaApplication();
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(app::voiceApp);
		ARItySipInitiator.call(asterisk.getSipHostPort(), asterisk.getContainerIpAddress() ,"1234").get();
		arity.disconnect();
		assertTrue(app.isSucceeded);
	}

	@Test(timeout = 30000)
	public void testRegisterLambda() throws Exception {
		AtomicInteger numCalled = new AtomicInteger(0);
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(call -> {
			call.answer().run().thenCompose(v -> call.play("hello-world").loop(2).run())
			.thenAccept(pb -> {
				logger.info("finished playback in lambda! id: " + pb.getPlayback().getId());
				numCalled.addAndGet(1);
				System.err.println("!!!!!After playback finished, I think I was called: " + numCalled.get());
				logger.info("After playback finished, I think I was called: " + numCalled.get());
			}).handle(call::endCall).exceptionally(t -> {
				logger.severe("Error: " + t);
				return null;
			});
		});
		ARItySipInitiator.call(asterisk.getSipHostPort(), "127.0.0.1", "1234").get();
		arity.disconnect();
		logger.info("Finished with call, I think I was called: " + numCalled.get());
		assertEquals(1, numCalled.get());
	}

	@Test(timeout = 30000)
	public void testRegisterClass() throws Exception {
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(Application.class);
		arity.disconnect();
	}

	@Test(timeout = 30000)
	// class problem
	public void testErrAbstractClass() throws Exception {
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(BrokenApp.class);
		ARItySipInitiator.call(asterisk.getSipHostPort(), asterisk.getContainerIpAddress() ,"1234").get();
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

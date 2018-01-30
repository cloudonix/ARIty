package io.cloudonix.arity;

import static org.junit.Assert.fail;

import java.net.URISyntaxException;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.cloudonix.arity.errors.ConnectionFailedException;

class ARItyTest {

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

		public void voiceApp(CallController call) {
			call.answer().run().thenCompose(v -> call.play("hello-world").loop(2).run()).thenCompose(pb -> {
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return call.hangup().run();
			}).handle(call::endCall).exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});
		}

	}

	abstract class BrokenApp extends CallController {
	}

	static AsteriskContainer asterisk = new AsteriskContainer();

	private final static Logger logger = Logger.getLogger(ARItyTest.class.getName());

	@BeforeAll
	static void beforeTesting() {
		asterisk.start();
	}

	@Test
	void testARIty() throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		arity.disconnect();

	}

	@Test
	void testRegisterSupplier() throws ConnectionFailedException, URISyntaxException {

		Application app = new Application();
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		arity.registerVoiceApp(app::voiceApp);
		arity.disconnect();
	}

	@Test
	void testReristerLambda() throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		arity.registerVoiceApp(call -> {
			call.answer().run().thenCompose(v -> call.play("hello-world").loop(2).run()).thenCompose(pb -> {
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return call.hangup().run();
			}).handle(call::endCall).exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});
		});
		arity.disconnect();
	}

	@Test
	void testRegisterClass() throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		arity.registerVoiceApp(Application.class);
		arity.disconnect();
	}

	@Test
	void testHangupErr() throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		arity.registerVoiceApp(BrokenApp.class);
		//arity.getCallSupplier().get()
		arity.disconnect();

	}

	@AfterAll
	static void afterTesting() {
		asterisk.stop();
	}

}

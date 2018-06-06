package io.cloudonix.arity;

import static org.junit.Assert.fail;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.junit.ClassRule;
import org.junit.Test;

import io.cloudonix.ARItySipInitiator;
import io.cloudonix.arity.errors.ConnectionFailedException;

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
	
	@ClassRule
	public static AsteriskContainer asterisk = new AsteriskContainer();
	
	private final static Logger logger = Logger.getLogger(ARItyTest.class.getName());
	
	@Test
	public void testARIty() throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.disconnect();
	}

	@Test
	public void testRegisterSupplier() throws Exception{
		Application app = new Application();
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(app::voiceApp);
		ARItySipInitiator.call(asterisk.getSipServerURL(), asterisk.getContainerIpAddress() ,"1234").get();
		arity.disconnect();
		if(app.isSucceeded)
			logger.info("test succeeded! ");
		else
			fail();
	}

	@Test
	public void testReristerLambda() throws Exception {
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(call -> {
			call.answer().run().thenCompose(v -> call.play("hello-world").loop(2).run()).thenCompose(pb -> {
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return call.hangup().run();
			}).handle(call::endCall).exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});
		});
		ARItySipInitiator.call(asterisk.getSipServerURL(), asterisk.getContainerIpAddress() ,"1234").get();
		arity.disconnect();
	}

	@Test
	public void testRegisterClass() throws Exception {
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(Application.class);
		arity.disconnect();
	}

	@Test
	// class problem
	public void testErrAbstractClass() throws Exception {
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(BrokenApp.class);
		ARItySipInitiator.call(asterisk.getSipServerURL(), asterisk.getContainerIpAddress() ,"1234").get();
		arity.disconnect();
	}
	
	@Test
	// error while running an application
	public void testErrRun() throws Exception {
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(call->{
			call.play("hello-world").loop(2).run();
		});
		ARItySipInitiator.call(asterisk.getSipServerURL(), asterisk.getContainerIpAddress() ,"1234").get();
		arity.disconnect();
	}
	@Test
	//application is not registered
	public void testNotRegisteredApp() throws Exception {
		Application app = new Application();
		ARIty arity = new ARIty(asterisk.getAriURL(), "stasisApp", "testuser", "123");
		app.run();
		ARItySipInitiator.call(asterisk.getSipServerURL(), asterisk.getContainerIpAddress() ,"1234").get();
		arity.disconnect();
	}

}

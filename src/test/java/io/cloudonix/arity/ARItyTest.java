package io.cloudonix.arity;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.TooManyListenersException;
import java.util.logging.Logger;

import javax.sdp.SdpException;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;

import org.junit.ClassRule;
import org.junit.jupiter.api.Test;

import io.cloudonix.ARItySipInitiator;
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
	@ClassRule
	static AsteriskContainer asterisk = new AsteriskContainer();

	private final static Logger logger = Logger.getLogger(ARItyTest.class.getName());
	
	@Test
	void testARIty() throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty(asterisk.getAriIP(), "stasisApp", "testuser", "123");
		arity.disconnect();
	}

	@Test
	void testRegisterSupplier() throws ConnectionFailedException, URISyntaxException, InvalidArgumentException, TooManyListenersException, ParseException, SipException, SdpException {
		Application app = new Application();
		ARIty arity = new ARIty(asterisk.getAriIP(), "stasisApp", "testuser", "123");
		arity.registerVoiceApp(app::voiceApp);
		ARItySipInitiator.call(asterisk.getSipServerIP(), asterisk.getContainerIpAddress() ,"1234");
		/*while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Thread is not sleeping");
			}
		}*/
		arity.disconnect();
	}

	@Test
	void testReristerLambda() throws ConnectionFailedException, URISyntaxException, InvalidArgumentException, TooManyListenersException, ParseException, SipException, SdpException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "testuser", "123");
		arity.registerVoiceApp(call -> {
			call.answer().run().thenCompose(v -> call.play("hello-world").loop(2).run()).thenCompose(pb -> {
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return call.hangup().run();
			}).handle(call::endCall).exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});
		});
		ARItySipInitiator.call(asterisk.getSipServerIP(), asterisk.getContainerIpAddress() ,"1234");
		arity.disconnect();
	}

	@Test
	void testRegisterClass() throws ConnectionFailedException, URISyntaxException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "testuser", "123");
		arity.registerVoiceApp(Application.class);
		arity.disconnect();
	}

	@Test
	// class problem
	void testErrAbstractClass() throws ConnectionFailedException, URISyntaxException, InvalidArgumentException, TooManyListenersException, ParseException, SipException, SdpException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		arity.registerVoiceApp(BrokenApp.class);
		ARItySipInitiator.call(asterisk.getSipServerIP(), asterisk.getContainerIpAddress() ,"1234");
		arity.disconnect();
	}
	
	@Test
	// error while running an application
	void testErrRun() throws ConnectionFailedException, URISyntaxException, InvalidArgumentException, TooManyListenersException, ParseException, SipException, SdpException {
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "userid", "secret");
		arity.registerVoiceApp(call->{
			call.play("hello-world").loop(2).run();
		});
		ARItySipInitiator.call(asterisk.getSipServerIP(), asterisk.getContainerIpAddress() ,"1234");
		arity.disconnect();
	}
	@Test
	//application is not registered
	void testNotRegisteredApp() throws ConnectionFailedException, URISyntaxException, InvalidArgumentException, TooManyListenersException, ParseException, SipException, SdpException {
		Application app = new Application();
		ARIty arity = new ARIty("http://127.0.0.1:8088/", "stasisApp", "testuser", "123");
		app.run();
		ARItySipInitiator.call(asterisk.getSipServerIP(), asterisk.getContainerIpAddress() ,"1234");
		arity.disconnect();
	}

}

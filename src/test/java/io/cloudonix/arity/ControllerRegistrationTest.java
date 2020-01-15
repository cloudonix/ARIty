package io.cloudonix.arity;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudonix.ARItySipInitiator;
import io.cloudonix.test.support.AsteriskContainer;

public class ControllerRegistrationTest {

	public static class MyCallController extends CallController {
		@Override
		public CompletableFuture<Void> run() {
			runCount++;
			return hangup().run().thenAccept(v->{});
		}
	}

	public class PrivateMyCallController extends CallController {
		@Override
		public CompletableFuture<Void> run() {
			runCount++;
			return hangup().run().thenAccept(v->{});
		}
	}

	@Rule
	public AsteriskContainer asterisk = new AsteriskContainer();

	final static Logger logger = LoggerFactory.getLogger(ControllerRegistrationTest.class);

	volatile static int runCount = 0;

	@Test
	public void testRegisterClass() throws Exception {
		runCount = 0;
		asterisk.getARIty().registerVoiceApp(MyCallController.class);
		ARItySipInitiator.call(asterisk.getSipHostPort(), "0.0.0.0" ,"1234").get();
		assertTrue(runCount > 0);
	}

	@Test
	public void testRegisterSupplier() throws Exception {
		runCount = 0;
		asterisk.getARIty().registerVoiceApp(PrivateMyCallController::new);
		ARItySipInitiator.call(asterisk.getSipHostPort(), "0.0.0.0" ,"1234").get();
		assertTrue(runCount > 0);
	}

	@Test
	public void testRegisterLambda() throws Exception {
		runCount = 0;
		asterisk.getARIty().registerVoiceApp(call -> {
			runCount++;
			call.hangup().run();
		});
		ARItySipInitiator.call(asterisk.getSipHostPort(), "0.0.0.0" ,"1234").get();
		assertTrue(runCount > 0);
	}

}

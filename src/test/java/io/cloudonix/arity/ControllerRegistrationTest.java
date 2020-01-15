package io.cloudonix.arity;

import static org.junit.Assert.assertEquals;
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

	abstract class BrokenApp extends CallController {
	}

	@Rule
	public AsteriskContainer asterisk = new AsteriskContainer();

	final static Logger logger = LoggerFactory.getLogger(ControllerRegistrationTest.class);

	volatile static int runCount = 0;

	@Test(timeout = 15000)
	public void testRegisterClass() throws Exception {
		runCount = 0;
		asterisk.getARIty().registerVoiceApp(MyCallController.class);
		int status = ARItySipInitiator.call(asterisk.getSipHostPort(), "0.0.0.0" ,"1234").get();
		assertTrue(runCount > 0);
		assertEquals(603, status);
	}

	@Test(timeout = 15000)
	public void testRegisterSupplier() throws Exception {
		runCount = 0;
		asterisk.getARIty().registerVoiceApp(PrivateMyCallController::new);
		int status = ARItySipInitiator.call(asterisk.getSipHostPort(), "0.0.0.0" ,"1234").get();
		assertTrue(runCount > 0);
		assertEquals(603, status);
	}

	@Test(timeout = 15000)
	public void testRegisterLambda() throws Exception {
		runCount = 0;
		asterisk.getARIty().registerVoiceApp(call -> {
			runCount++;
			call.answer().run()
			.thenCompose(v -> CompletableFuture.runAsync(() -> {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}))
			.thenCompose(v -> call.hangup().run());
		});
		int status = ARItySipInitiator.call(asterisk.getSipHostPort(), "0.0.0.0" ,"1234").get();
		assertTrue(runCount > 0);
		assertEquals(200, status);
	}

	@Test(timeout = 12000)
	public void testErrAbstractClass() throws Exception {
		asterisk.getARIty().registerVoiceApp(BrokenApp.class);
		int status = ARItySipInitiator.call(asterisk.getSipHostPort(), asterisk.getContainerIpAddress() ,"1234").get();
		assertEquals(603, status);
	}

}

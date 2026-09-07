package io.cloudonix.arity;

import org.junit.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

public class ARItyConnectionFailureTest {
	@RegisterExtension
	static VertxExtension vertxExtension = new VertxExtension();

	@Test(timeout=10000, expected = io.cloudonix.arity.errors.ConnectionFailedException.class)
	public void testConnectionailure(Vertx vertx) throws Exception {
		ARIty arity = ARIty.create(vertx, new ARItyOptions().uri("http://localhost:18088/").appName("stasisApp").login("testuser").password("123"));
		arity.disconnect();
	}
}

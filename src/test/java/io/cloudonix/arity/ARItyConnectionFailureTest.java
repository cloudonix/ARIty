package io.cloudonix.arity;

import org.junit.Test;

public class ARItyConnectionFailureTest {

	@Test(timeout=10000, expected = io.cloudonix.arity.errors.ConnectionFailedException.class)
	public void testConnectionailure() throws Exception {
		ARIty arity = new ARIty("http://localhost:18088/", "stasisApp", "testuser", "123");
		arity.disconnect();
	}
}

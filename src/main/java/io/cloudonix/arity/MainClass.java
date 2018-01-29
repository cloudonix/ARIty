package io.cloudonix.arity;

import java.net.URISyntaxException;
import java.util.logging.Logger;

import io.cloudonix.arity.errors.ConnectionFailedException;

public class MainClass {

	private static String URI = "http://127.0.0.1:8088/";

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
	//	Application app = new Application();
		// Create the service of ARI
		ARIty ari = null;
		try {
			ari = new ARIty(URI, "stasisApp", "userid", "secret");

		} catch (Throwable e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	//	 ari.registerVoiceApp(app::voiceApp);

	}
}

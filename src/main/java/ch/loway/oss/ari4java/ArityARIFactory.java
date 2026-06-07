package ch.loway.oss.ari4java;

import java.net.URISyntaxException;

import ch.loway.oss.ari4java.tools.ARIException;

/**
 * ARIty's ARI factory helper class
 * We use this to be able to access the "package only" "internal" ARI4Java API
 */
public class ArityARIFactory extends AriFactory {

	public static void setupHttpClient(String uri, String login, String password) throws ARIException {
		try {
			var client = new ArityNettyHttpClient();
			client.initialize(uri, login, password);
			nettyHttpClient = client;
		} catch (URISyntaxException e) {
			throw new ARIException(e);
		}
	}

}

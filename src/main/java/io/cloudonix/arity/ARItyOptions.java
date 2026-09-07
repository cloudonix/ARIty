package io.cloudonix.arity;

import java.net.URISyntaxException;
import java.util.function.Consumer;

import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.tools.WsClient;
import ch.loway.oss.ari4java.tools.http.NettyHttpClient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter @Setter @Accessors(chain = true, fluent = true)
public class ARItyOptions {
	private String uri;
	private String appName;
	private String login;
	private String password;
	private boolean openWebSocket = true;
	private AriVersion ariVersion = AriVersion.IM_FEELING_LUCKY;
	private Consumer<Exception> errorHandler = e -> {};
	private int connectionAttempts = 0; // do not change
	private NettyHttpClient httpClient;

	NettyHttpClient createHttpClient() throws URISyntaxException {
		if (httpClient != null)
			return httpClient;
		httpClient = new NettyHttpClient();
		httpClient.initialize(uri, login, password);
		if (connectionAttempts != 0)
			httpClient.setMaxReconnectCount(connectionAttempts);
		return httpClient;
	}

	WsClient createWsClient() throws URISyntaxException {
		return createHttpClient();
	}
}
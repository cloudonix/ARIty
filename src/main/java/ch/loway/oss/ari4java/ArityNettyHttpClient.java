package ch.loway.oss.ari4java;

import java.time.Duration;

import ch.loway.oss.ari4java.tools.http.NettyHttpClient;
import io.netty.channel.ChannelOption;

public class ArityNettyHttpClient extends NettyHttpClient {

	
	private static final Duration CONNECTION_TIMEOUT_SEC = Duration.ofSeconds(2);

	@Override
	protected void initHttpBootstrap() {
		super.initHttpBootstrap();
		httpBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int)CONNECTION_TIMEOUT_SEC.toMillis());
	}

}
package io.cloudonix.arity.impl.ari4java;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.models.Message;
import ch.loway.oss.ari4java.tools.AriConnectionEvent;
import ch.loway.oss.ari4java.tools.AriWSCallback;
import ch.loway.oss.ari4java.tools.RestException;

public class ARI4JavaEventHandler implements AriWSCallback<Message> {
	private Logger log = LoggerFactory.getLogger(getClass());

	private ARI4JavaConnection connection;
	private CompletableFuture<Void> connectionCompletion;

	public ARI4JavaEventHandler(ARI4JavaConnection connection) {
		this.connection = connection;
		connectionCompletion = new CompletableFuture<Void>();
	}

	@Override
	public void onSuccess(Message result) {
		connection.receiveEvent(result);
	}

	@Override
	public void onFailure(RestException e) {
		log.error("Unexpected error from WebSocket connection handler", e);
	}

	@Override
	public void onConnectionEvent(AriConnectionEvent event) {
		if (event == AriConnectionEvent.WS_CONNECTED)
			connectionCompletion.complete(null);
		else
			connection.disconnected();
	}

	/**
	 * Return a promise for the event websocket to connection
	 * @return a promise that will resolve when the ARI events websocket connects
	 */
	public CompletableFuture<Void> whenConnected() {
		return connectionCompletion;
	}

}

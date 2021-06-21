package io.cloudonix.arity.impl.ari4java;

import static java.util.concurrent.CompletableFuture.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.models.*;
import ch.loway.oss.ari4java.tools.ARIException;
import ch.loway.oss.ari4java.tools.AriConnectionEvent;
import ch.loway.oss.ari4java.tools.AriWSCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.CallState;
import io.cloudonix.arity.Operation.AriOperation;
import io.cloudonix.arity.errors.ConnectionFailedException;
import io.cloudonix.arity.impl.ARIConnection;

public class ARI4JavaConnection extends ARIConnection {
	
	private Logger log = LoggerFactory.getLogger(getClass());

	private ARIty arity;
	private String url;
	private String appName;
	private String username;
	private String password;
	private boolean receiveEvents;

	private ARI ari;
	private ARI4JavaEventHandler eventHandler;

	public ARI4JavaConnection(ARIty arity, String url, String appName, String username, String password, boolean receiveEvents) {
		this.arity = arity;
		this.url = url;
		this.appName = appName;
		this.username = username;
		this.password = password;
		this.receiveEvents = receiveEvents;
	}

	@Override
	public CompletableFuture<Void> connect(Version version) {
		try {
			ari = ARI.build(url, appName, username, password, toARIVersion(version));
			log.info("Ari created {}", url);
			log.info("Ari version: " + ari.getVersion());
			if (receiveEvents) {
				eventHandler = new ARI4JavaEventHandler(this);
				ari.events().eventWebsocket(appName).setSubscribeAll(true).execute(eventHandler);
				return eventHandler.whenConnected()
						.thenRun(() -> log.info("Websocket is open"));
			} else {
				return completedFuture(null);
			}
		} catch (ARIException e) {
			log.error("Connection failed: ",e);
			return failedFuture(new ConnectionFailedException(e));
		}
	}

	private static AriVersion toARIVersion(Version version) {
		if (version == Version.LATEST)
			return AriVersion.IM_FEELING_LUCKY;
		try {
			return AriVersion.fromVersionString(version.toString());
		} catch (ch.loway.oss.ari4java.tools.ARIException e) {
			return AriVersion.IM_FEELING_LUCKY;
		}
	}

	private <V> CompletableFuture<V> toFuture(AriOperation<V> op) {
		StackTraceElement[] caller = getCallingStack();
		CompletableFuture<V> cf = new CompletableFuture<V>();
		AriWSCallback<V> ariCallback = new AriWSCallback<V>() {

			@Override
			public void onSuccess(V result) {
				log.trace("Got async result: success for {}", op);
				CompletableFuture.runAsync(() -> {
					log.trace("Dispatching sucessful async result {} for {}", result, op);
					cf.complete(result);
				}, asyncExecutor);
			}

			@Override
			public void onFailure(RestException e) {
				log.debug("Got async result: failure for {}", op);
				CompletableFuture.runAsync(() -> {
					log.trace("Dispatching failed async result {} for {}", e.toString(), op);
					cf.completeExceptionally(rewrapError("ARI operation failed: " + e, caller, e));
				}, asyncExecutor);
			}

			@Override
			public void onConnectionEvent(AriConnectionEvent event) {
				// TODO Auto-generated method stub
				
			}
		};

		try {
			op.accept(ariCallback);
		} catch (RestException e1) {
			CompletableFuture.runAsync(() -> cf.completeExceptionally(e1));
		}
		return cf;
	}

	private static StackTraceElement[] getCallingStack() {
		return Stream.of(new Exception().fillInStackTrace().getStackTrace()).skip(1).collect(Collectors.toList())
				.toArray(new StackTraceElement[] {});
	}

	public static CompletionException rewrapError(String message, StackTraceElement[] originalStack, Throwable cause) {
		while (cause instanceof CompletionException)
			cause = cause.getCause();
		CompletionException wrap = new CompletionException(message, cause);
		wrap.setStackTrace(originalStack);
		return wrap;
	}

	/**
	 * get the channel id of the current event. if no channel id to this event, null
	 * is returned
	 *
	 * @param event event message that we are checking
	 * @return
	 */
	private String getEventChannelId(Message event) {
		if (event instanceof DeviceStateChanged || event instanceof BridgeCreated || event instanceof BridgeDestroyed)
			return null; // skip this, it never has a channel

		if (event instanceof ch.loway.oss.ari4java.generated.models.Dial)
			return ((ch.loway.oss.ari4java.generated.models.Dial) event).getPeer().getId();

		if (event instanceof PlaybackStarted)
			return ((PlaybackStarted) event).getPlayback().getTarget_uri()
					.substring(((PlaybackStarted) event).getPlayback().getTarget_uri().indexOf(":") + 1);

		if (event instanceof PlaybackFinished)
			return ((PlaybackFinished) event).getPlayback().getTarget_uri()
					.substring(((PlaybackFinished) event).getPlayback().getTarget_uri().indexOf(":") + 1);

		if (event instanceof RecordingStarted)
			return ((RecordingStarted) event).getRecording().getTarget_uri()
					.substring(((RecordingStarted) event).getRecording().getTarget_uri().indexOf(":") + 1);
		if (event instanceof RecordingFinished)
			return ((RecordingFinished) event).getRecording().getTarget_uri()
					.substring(((RecordingFinished) event).getRecording().getTarget_uri().indexOf(":") + 1);

		try {
			Class<?> msgClass = event.getClass();
			Object chan = msgClass.getMethod("getChannel").invoke(event);
			if (Objects.nonNull(chan))
				return ((Channel) chan).getId();
			log.warn("Channel ID is not set for event " + event);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			log.warn("Can not get channel id for event " + event + ": " + e);
		}
		return null;
	}
	
	@Override
	public CompletableFuture<CallState> getCallState(String channelId) {
		return this.<Channel>toFuture(h -> ari.channels().get(channelId).execute(h))
				.thenApply(chan -> new ARI4JavaCallState(chan, arity, ari));
	}

	public void disconnected() {
		log.warn("ARI disconnected!");
	}

	public void receiveEvent(Message event) {
		String channelId = getEventChannelId(event);
		log.trace("Received WebSocket event {}:{}", event.getType(), channelId);
		if (event instanceof StasisStart) {
			if ("h".equals(((StasisStart) event).getChannel().getDialplan().getExten())) {
				log.debug("Ignoring Stasis Start with 'h' extension, listen on channel hangup event if you want to handle hangups");
				return;
			}
			arity.handleStasisStart(new ARI4JavaCallState(((StasisStart) event).getChannel(), arity, ari));
		} else if (event instanceof StasisEnd) {
			arity.handleStasisEnd(channelId);
		}
	}

}

package io.cloudonix.arity;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.Message;
import io.cloudonix.arity.errors.ConnectionFailedException;
import io.vertx.core.Vertx;

public interface ARIty {
	
	static ARIty create(Vertx vertx) throws ConnectionFailedException, URISyntaxException {
		return create(vertx, new ARItyOptions());
		}

	static ARIty create(Vertx vertx, ARItyOptions options) throws ConnectionFailedException, URISyntaxException {
		return new ARItyImpl(vertx, options);
		}

	ARIty setAutoBindBridges(boolean shouldAutoBind);
	ARIty setExecutorService(ExecutorService service);
	void registerVoiceApp(Supplier<CallController> controllerSupplier);
	void registerVoiceApp(Class<? extends CallController> controllerClass) throws NoSuchMethodException;
	void registerVoiceApp(Consumer<CallController> callHandler);
	void registerVoiceApp(String channelId, Supplier<CallController> controllerSupplier);
	void registerVoiceApp(String channelId, Class<? extends CallController> controllerClass) throws NoSuchMethodException;
	void registerVoiceApp(String channelId, Consumer<CallController> callHandler);
	CompletableFuture<CallState> waitForNewCallState(String channelId);
	CompletableFuture<CallState> waitForNewCallState(String channelId, Duration timeout);
	CompletableFuture<CallController> waitForNewCall(String channelId);
	CompletableFuture<CallController> waitForNewCall(String channelId, Duration timeout);
	<T extends CallController> CompletableFuture<T> initFromChannel(T controller, String channelId);
	CompletableFuture<CallState> getCallState(String channelId);
	<T extends Message> EventHandler<T> addEventHandler(Class<T> type, String channelId, BiConsumer<T,EventHandler<T>> eventHandler);
	<T extends Message> EventHandler<T> addGeneralEventHandler(Class<T> type, BiConsumer<T, EventHandler<T>> eventHandler);
	<T extends Message> void removeEventHandler(EventHandler<T>handler);
	<T extends Message> EventHandler<T> listenForOneTimeEvent(Class<T> type, String channelId, Consumer<T> eventHandler);
	void disconnect();
	Dial dial(String callerId, String destination);
	CompletableFuture<List<Channel>> getActiveChannels();
	Channels channels();
	Bridges bridges();

	String getConnetion();
	String getAppName();
}

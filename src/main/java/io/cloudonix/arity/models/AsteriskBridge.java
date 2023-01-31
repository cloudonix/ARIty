package io.cloudonix.arity.models;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.loway.oss.ari4java.generated.actions.ActionBridges;
import ch.loway.oss.ari4java.generated.models.Bridge;
import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.ChannelEnteredBridge;
import ch.loway.oss.ari4java.generated.models.ChannelLeftBridge;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import ch.loway.oss.ari4java.generated.models.Playback;
import ch.loway.oss.ari4java.generated.models.PlaybackFinished;
import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.Operation;
import io.cloudonix.arity.Bridges.BridgeType;
import io.cloudonix.arity.errors.bridge.BridgeNotFoundException;
import io.cloudonix.arity.errors.bridge.ChannelNotAllowedInBridge;
import io.cloudonix.arity.errors.bridge.ChannelNotInBridgeException;
import io.cloudonix.arity.helpers.Futures;

public class AsteriskBridge {

	private ARIty arity;
	private Bridge bridge;
	private String bridgeId;
	private ActionBridges api;

	@SuppressWarnings("deprecation")
	public AsteriskBridge(ARIty arity, Bridge bridge) {
		this.arity = arity;
		this.bridge = bridge;
		bridgeId = bridge.getId();
		this.api = arity.getAri().bridges();
	}
	
	public CompletableFuture<Void> destroy() {
		return Operation.retry(cb -> api.destroy(bridgeId).execute(cb), mapExceptions(bridgeId));
	}
	
	/* getters */
	
	public String getId() {
		return bridgeId;
	}
	
	public String getName() {
		return bridge.getName();
	}
	
	private CompletableFuture<AsteriskBridge> reload() {
		return Operation.<Bridge>retry(cb -> api.get(bridgeId).execute(cb), mapExceptions(bridgeId))
				.thenApply(b -> { bridge = b; return this; });
	}
	
	public CompletableFuture<AsteriskBridge> update(BridgeType... types) {
		var bridgeTypes = BridgeType.merge(types, BridgeType.mixing); // make sure that types includes either "mixing" or "holding"
		var bridgeType = Stream.of(bridgeTypes).map(Object::toString).collect(Collectors.joining(","));
		return Operation.<ch.loway.oss.ari4java.generated.models.Bridge>retry(cb -> api.createWithId(bridgeId)
				.setType(bridgeType).execute(cb)).thenApply(b -> this);
	}
	
	public CompletableFuture<Boolean> isActive() {
		return reload().thenApply(v -> true).exceptionally(Futures.on(BridgeNotFoundException.class, e -> false));
	}
	
	/* Channel Management */
	
	public CompletableFuture<Integer> getChannelCount() {
		return reload().thenApply(v -> bridge.getChannels().size());
	}
	
	public CompletableFuture<List<String>> getChannels() {
		return reload().thenApply(v -> bridge.getChannels());
	}
	
	public CompletableFuture<Void> addChannel(AsteriskChannel channel) {
		return addChannel(channel.getId());
	}
	
	public CompletableFuture<Void> addChannel(AsteriskChannel channel, boolean confirmWasAdded) {
		return addChannel(channel.getId(), confirmWasAdded);
	}
	
	public CompletableFuture<Void> addChannel(Channel channel) {
		return addChannel(channel.getId());
	}
	
	public CompletableFuture<Void> addChannel(Channel channel, boolean confirmWasAdded) {
		return addChannel(channel.getId(), confirmWasAdded);
	}
		
	public CompletableFuture<Void> addChannel(String channelId) {
		return addChannel(channelId, false);
	}

	public CompletableFuture<Void> addChannel(String channelId, boolean confirmWasAdded) {
		CompletableFuture<Void> waitForAdded = new CompletableFuture<>();
		if (confirmWasAdded)
			arity.listenForOneTimeEvent(ChannelEnteredBridge.class, channelId, e -> waitForAdded.complete(null));
		else
			waitForAdded.complete(null);
		return Operation.<Void>retry(cb -> api.addChannel(bridgeId, channelId).setRole("member").execute(cb), mapExceptions(bridgeId))
				.thenCompose(v -> waitForAdded);
	}
	
	public CompletableFuture<Void> removeChannel(AsteriskChannel channel) {
		return removeChannel(channel.getId());
	}
	
	public CompletableFuture<Void> removeChannel(AsteriskChannel channel, boolean confirmWasAdded) {
		return removeChannel(channel.getId(), confirmWasAdded);
	}
	
	public CompletableFuture<Void> removeChannel(Channel channel) {
		return removeChannel(channel.getId());
	}
	
	public CompletableFuture<Void> removeChannel(Channel channel, boolean confirmWasAdded) {
		return removeChannel(channel.getId(), confirmWasAdded);
	}
		
	public CompletableFuture<Void> removeChannel(String channelId) {
		return removeChannel(channelId, false);
	}

	public CompletableFuture<Void> removeChannel(String channelId, boolean confirmWasRemoved) {
		CompletableFuture<Void> waitForRemoved = new CompletableFuture<>();
		if (confirmWasRemoved)
			arity.listenForOneTimeEvent(ChannelLeftBridge.class, channelId, e -> waitForRemoved.complete(null));
		else
			waitForRemoved.complete(null);
		return Operation.<Void>retry(cb -> api.removeChannel(bridgeId, channelId).execute(cb), mapExceptions(bridgeId))
				.exceptionally(Futures.on(ChannelNotInBridgeException.class, e -> {
					waitForRemoved.complete(null);
					return null;
				}))
				.thenCompose(v -> waitForRemoved);
	}
	
	/* Recording */
	
	public CompletableFuture<AsteriskRecording> record() {
		return record(b -> {});
	}

	public CompletableFuture<AsteriskRecording> record(boolean playBeep, int maxDuration, int maxSilence, String terminateOnDTMF) {
		return record(b -> b.withPlayBeep(playBeep).withMaxDuration(maxDuration).withMaxSilence(maxSilence)
				.withTerminateOn(Objects.requireNonNull(terminateOnDTMF)));
	}

	public CompletableFuture<AsteriskRecording> record(boolean playBeep, int maxDuration, int maxSilence, String terminateOnDTMF, String ifExists) {
		return record(b -> b.withPlayBeep(playBeep).withMaxDuration(maxDuration).withMaxSilence(maxSilence)
				.withTerminateOn(Objects.requireNonNull(terminateOnDTMF)).withIfExists(ifExists));
	}
	
	public CompletableFuture<AsteriskRecording> record(Consumer<AsteriskRecording.Builder> withBuilder) {
		return Operation.<LiveRecording>retry(cb ->  AsteriskRecording.build(withBuilder).build(
				api.record(bridgeId, null, null), arity).execute(cb), mapExceptions(bridgeId))
				.thenApply(rec -> new AsteriskRecording(arity, rec));
	}

	/**
	 * Start to play music on hold to the bridge
	 * @param musicOnHoldClass the MoH class to tell Asterisk to use
	 * @return a promise that will resolve when the music on hold starts or reject if there is an unrecoverable error
	 */
	public CompletableFuture<Void> startMusicOnHold(String musicOnHoldClass) {
		return Operation.<Void>retry(cb -> api.startMoh(bridgeId).setMohClass(musicOnHoldClass).execute(cb), mapExceptions(bridgeId));
	}

	/**
	 * Stops playing music on hold to the bridge
	 * @return a promise that will resolve when the music on hold stops or reject if there is an unrecoverable error
	 */
	public CompletableFuture<Void> stopMusicOnHold() {
		return Operation.<Void>retry(cb -> api.stopMoh(bridgeId).execute(cb), mapExceptions(bridgeId));
	}
	
	/**
	 * Play media to the bridge
	 * @param fileToPlay name of the file to be played
	 * @return a promise that will resolve when the media finishes playing or reject if there is an unrecoverable error
	 */
	public CompletableFuture<Playback> playMedia(String fileToPlay) {
		String playbackId = UUID.randomUUID().toString();
		return Operation.<Playback>retry(
				cb -> api.play(bridgeId, "sound:" + fileToPlay).setLang("en").setPlaybackId(playbackId).execute(cb), mapExceptions(bridgeId))
				.thenCompose(result -> {
					CompletableFuture<Playback> future = new CompletableFuture<Playback>();
					arity.addEventHandler(PlaybackFinished.class, bridgeId, (pbf, se) -> {
						if (!(pbf.getPlayback().getId().equals(playbackId)))
							return;
						future.complete(pbf.getPlayback());
						se.unregister();
					});
					return future;
				});
	}

	/* helpers */
	
	@Override
	public String toString() {
		return String.format("ARI/Bridges:%s(%s)", bridgeId, bridge.getName());
	}
	
	private Function<Throwable,Exception> mapExceptions(String bridgeId) {
		return t -> {
			switch (t.getMessage()) {
			case "Bridge not found": return new BridgeNotFoundException(bridgeId, t);
			case "Bridge not in Stasis application": return new BridgeNotFoundException(bridgeId, t);
			case "Channel not found": return new ChannelNotInBridgeException(bridgeId, t);
			case "Channel not in Stasis application": return new ChannelNotAllowedInBridge(bridgeId, t.getMessage());
			case "Channel not in this bridge": return new ChannelNotInBridgeException(bridgeId, t);
			}
			return null;
		};
	}
}

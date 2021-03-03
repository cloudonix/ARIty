package io.cloudonix.arity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import ch.loway.oss.ari4java.generated.actions.ActionBridges;
import io.cloudonix.arity.models.AsteriskBridge;

public class Bridges {

	private ARIty arity;
	private ActionBridges api;

	public Bridges(ARIty arity) {
		this.arity = arity;
		this.api = arity.getAri().bridges();
	}

	public CompletableFuture<AsteriskBridge> create() {
		String id = UUID.randomUUID().toString();
		return create(id, id);
	}
	
	public CompletableFuture<AsteriskBridge> create(String bridgeName) {
		return create(UUID.randomUUID().toString(), bridgeName);
	}

	public CompletableFuture<AsteriskBridge> create(String bridgeId, String bridgeName) {
		return create(bridgeId, bridgeName, "mixing");
	}
	
	public CompletableFuture<AsteriskBridge> create(String bridgeId, String bridgeName, String bridgeType) {
		return Operation.<ch.loway.oss.ari4java.generated.models.Bridge>retry(cb -> api.create()
				.setBridgeId(bridgeId).setName(bridgeName).setType(bridgeType).execute(cb))
				.thenApply(b -> new AsteriskBridge(arity, b));
	}
	
	public CompletableFuture<AsteriskBridge> get(String bridgeId) {
		return Operation.<ch.loway.oss.ari4java.generated.models.Bridge>retry(cb -> api.get(bridgeId).execute(cb))
				.thenApply(this::get);
	}

	public AsteriskBridge get(ch.loway.oss.ari4java.generated.models.Bridge bridge) {
		return new AsteriskBridge(arity, bridge);
	}

	public CompletableFuture<AsteriskBridge> get(Bridge boundBridge) {
		return get(boundBridge.getId());
	}

}

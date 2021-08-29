package io.cloudonix.arity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.loway.oss.ari4java.generated.actions.ActionBridges;
import io.cloudonix.arity.models.AsteriskBridge;

public class Bridges {
	
	public enum BridgeType {
		mixing, holding, dtmf_events, proxy_media, video_sfu, video_single;

		public static BridgeType[] merge(BridgeType[] types, BridgeType ...othertypes) {
			var stypes = new HashSet<>(Set.of(types));
			for (var o : othertypes) {
				switch (o) {
				case mixing:
				case holding:
					if (stypes.contains(BridgeType.mixing) || stypes.contains(BridgeType.holding))
						continue;
				default:
					stypes.add(o);
				}
			}
			return stypes.toArray(new BridgeType[] {});
		}
	}

	private ARIty arity;
	private ActionBridges api;

	public Bridges(ARIty arity) {
		this.arity = arity;
		this.api = arity.getAri().bridges();
	}
	
	public CompletableFuture<List<AsteriskBridge>> list() {
		return Operation.<List<ch.loway.oss.ari4java.generated.models.Bridge>>retry(cb -> api.list().execute(cb))
				.thenApply(l -> l.stream().map(b -> new AsteriskBridge(arity, b)).collect(Collectors.toList()));
	}

	public CompletableFuture<AsteriskBridge> create(BridgeType... types) {
		String id = UUID.randomUUID().toString();
		return create(id, id, types);
	}
	
	public CompletableFuture<AsteriskBridge> create(String bridgeName, BridgeType... types) {
		return create(UUID.randomUUID().toString(), bridgeName, types);
	}

	public CompletableFuture<AsteriskBridge> create(String bridgeId, String bridgeName, BridgeType... types) {
		var bridgeTypes = BridgeType.merge(types, BridgeType.mixing); // make sure that types includes either "mixing" or "holding"
		var bridgeType = Stream.of(bridgeTypes).map(Object::toString).collect(Collectors.joining(","));
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

	public CompletableFuture<AsteriskBridge> get(Bridge bridge) {
		return get(bridge.getId());
	}

}

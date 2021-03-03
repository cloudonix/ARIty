package io.cloudonix.arity.models;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import ch.loway.oss.ari4java.generated.actions.ActionBridges;
import ch.loway.oss.ari4java.generated.actions.requests.BridgesRecordPostRequest;
import ch.loway.oss.ari4java.generated.models.Bridge;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.Operation;

public class AsteriskBridge {

	private Bridge bridge;
	private ActionBridges api;

	public AsteriskBridge(ARIty arity, Bridge bridge) {
		this.bridge = bridge;
		this.api = arity.getAri().bridges();
	}
	
	public CompletableFuture<Void> destroy() {
		return Operation.retry(cb -> api.destroy(bridge.getId()).execute(cb));
	}
	
	public class RecordBuilder {
		private String filename = UUID.randomUUID().toString();
		private String format = "slin";
		private Boolean playBeep;
		private Integer maxDuration;
		private Integer maxSilence;
		private String dtmf;
		public RecordBuilder withName(String filename) {
			this.filename = filename;
			return this;
		}
		public RecordBuilder withFormat(String format) {
			this.format = format;
			return this;
		}
		public RecordBuilder withPlayBeep(boolean playBeep) {
			this.playBeep = playBeep;
			return this;
		}
		public RecordBuilder withMaxDuration(int maxDuration) {
			this.maxDuration = maxDuration;
			return this;
		}
		public RecordBuilder withMaxSilence(int maxSilence) {
			this.maxSilence = maxSilence;
			return this;
		}
		public RecordBuilder withTerminateOn(String dtmf) {
			this.dtmf = dtmf;
			return this;
		}
		private BridgesRecordPostRequest build(BridgesRecordPostRequest req) {
			req.setFormat(format);
			req.setName(filename);
			if (playBeep != null) req.setBeep(playBeep);
			if (maxDuration != null) req.setMaxDurationSeconds(maxDuration);
			if (maxSilence != null) req.setMaxSilenceSeconds(maxSilence);
			if (dtmf != null) req.setTerminateOn(dtmf);
			return req;
		}
	}

	public CompletableFuture<LiveRecording> record() {
		return record(b -> {});
	}

	public CompletableFuture<LiveRecording> record(Consumer<RecordBuilder> withBuilder) {
		RecordBuilder req = new RecordBuilder();
		withBuilder.accept(req);
		return Operation.retry(cb -> req.build(api.record(bridge.getId(), null, null)).execute(cb));
	}
	
	public CompletableFuture<LiveRecording> record(boolean playBeep, int maxDuration, int maxSilence, String terminateOnDTMF) {
		return record(b -> b.withPlayBeep(playBeep).withMaxDuration(maxDuration).withMaxSilence(maxSilence).withTerminateOn(terminateOnDTMF));
	}

}

package io.cloudonix.arity.models;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import ch.loway.oss.ari4java.generated.actions.ActionRecordings;
import ch.loway.oss.ari4java.generated.actions.requests.BridgesRecordPostRequest;
import ch.loway.oss.ari4java.generated.actions.requests.ChannelsRecordPostRequest;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import ch.loway.oss.ari4java.generated.models.RecordingFinished;
import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.Operation;
import io.cloudonix.arity.RecordingData;

public class AsteriskRecording {

	private ARIty arity;
	private LiveRecording rec;
	private ActionRecordings api;
	private RecordingData storedRecording;

	public AsteriskRecording(ARIty arity, LiveRecording rec) {
		this.arity = arity;
		this.rec = rec;
		this.api = arity.getAri().recordings();
		this.storedRecording = new RecordingData(arity, rec.getName());
	}

	public static class Builder {
		private String filename = UUID.randomUUID().toString();
		private String format = "slin";
		private Boolean playBeep;
		private Integer maxDuration;
		private Integer maxSilence;
		private String dtmf;
		
		private Builder() {}

		public Builder withName(String filename) {
			this.filename = filename;
			return this;
		}

		public Builder withFormat(String format) {
			this.format = format;
			return this;
		}

		public Builder withPlayBeep(boolean playBeep) {
			this.playBeep = playBeep;
			return this;
		}

		public Builder withMaxDuration(int maxDuration) {
			this.maxDuration = maxDuration;
			return this;
		}

		public Builder withMaxSilence(int maxSilence) {
			this.maxSilence = maxSilence;
			return this;
		}

		public Builder withTerminateOn(String dtmf) {
			this.dtmf = dtmf;
			return this;
		}

		BridgesRecordPostRequest build(BridgesRecordPostRequest req) {
			req.setFormat(format);
			req.setName(filename);
			if (playBeep != null)
				req.setBeep(playBeep);
			if (maxDuration != null)
				req.setMaxDurationSeconds(maxDuration);
			if (maxSilence != null)
				req.setMaxSilenceSeconds(maxSilence);
			if (dtmf != null)
				req.setTerminateOn(dtmf);
			return req;
		}

		public ChannelsRecordPostRequest build(ChannelsRecordPostRequest req) {
			req.setFormat(format);
			req.setName(filename);
			if (playBeep != null)
				req.setBeep(playBeep);
			if (maxDuration != null)
				req.setMaxDurationSeconds(maxDuration);
			if (maxSilence != null)
				req.setMaxSilenceSeconds(maxSilence);
			if (dtmf != null)
				req.setTerminateOn(dtmf);
			return req;
		}
	}

	static Builder build(Consumer<Builder> withBuilder) {
		Builder builder = new Builder();
		withBuilder.accept(builder);
		return builder;
	}

	public String getName() {
		return rec.getName();
	}
	
	public String getFormat() {
		return rec.getFormat();
	}

	public String getState() {
		return rec.getState();
	}

	public String getCause() {
		return rec.getCause();
	}

	public int getDuration() {
		return rec.getDuration();
	}

	public int getTalkingDuration() {
		return rec.getTalking_duration();
	}

	public int getSilenceDuration() {
		return rec.getSilence_duration();
	}

	public CompletableFuture<AsteriskRecording> waitUntilEnd() {
		CompletableFuture<AsteriskRecording> waitForDone = new CompletableFuture<>();
		arity.addGeneralEventHandler(RecordingFinished.class, (e,se) -> {
			if (!e.getRecording().getName().equals(rec.getName())) return;
			se.unregister();
			waitForDone.complete(this);
		});
		return waitForDone;
	}
	
	public CompletableFuture<AsteriskRecording> cancel() {
		return cancel(false);
	}

	public CompletableFuture<AsteriskRecording> cancel(boolean waitUntilEnd) {
		CompletableFuture<AsteriskRecording> waitForDone = new CompletableFuture<>();
		if (waitUntilEnd)
			waitUntilEnd().thenAccept(waitForDone::complete);
		else
			waitForDone.complete(this);
		return Operation.<Void>retry(cb -> api.cancel(rec.getName()).execute(cb))
				.thenCompose(v -> waitForDone);
	}

	
	public CompletableFuture<AsteriskRecording> stop() {
		return stop(false);
	}
	
	public CompletableFuture<AsteriskRecording> stop(boolean waitUntilEnd) {
		CompletableFuture<AsteriskRecording> waitForDone = new CompletableFuture<>();
		if (waitUntilEnd)
			waitUntilEnd().thenAccept(waitForDone::complete);
		else
			waitForDone.complete(this);
		return Operation.<Void>retry(cb -> api.stop(rec.getName()).execute(cb))
				.thenCompose(v -> waitForDone);
	}

	public RecordingData getRecording() {
		return storedRecording;
	}

}
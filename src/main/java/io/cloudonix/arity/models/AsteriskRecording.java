package io.cloudonix.arity.models;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.actions.ActionRecordings;
import ch.loway.oss.ari4java.generated.actions.requests.BridgesRecordPostRequest;
import ch.loway.oss.ari4java.generated.actions.requests.ChannelsRecordPostRequest;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import ch.loway.oss.ari4java.generated.models.RecordingFinished;
import ch.loway.oss.ari4java.generated.models.RecordingStarted;
import io.cloudonix.arity.ARIty;
import io.cloudonix.arity.EventHandler;
import io.cloudonix.arity.Operation;
import io.cloudonix.arity.RecordingData;

public class AsteriskRecording {

	private ARIty arity;
	private LiveRecording rec;
	private ActionRecordings api;
	private RecordingData storedRecording;
	private static Logger log = LoggerFactory.getLogger(AsteriskRecording.class);

	public AsteriskRecording(ARIty arity, LiveRecording rec) {
		this.arity = arity;
		this.rec = rec;
		this.api = arity.getAri().recordings();
		this.storedRecording = new RecordingData(arity, rec.getName());
		storedRecording.setLiveRecording(rec);
	}

	public static class Builder {
		private String filename = UUID.randomUUID().toString();
		private String format = "slin";
		private Boolean playBeep;
		private Integer maxDuration;
		private Integer maxSilence;
		private String dtmf;
		private AtomicReference<Consumer<AsteriskRecording>> startHandler = new AtomicReference<>();
		
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

		BridgesRecordPostRequest build(BridgesRecordPostRequest req, ARIty arity) {
			req.setFormat(format);
			req.setName(filename);
			if (playBeep != null)
				req.setBeep(playBeep);
			if (maxDuration != null)
				req.setMaxDurationSeconds(maxDuration);
			if (maxSilence != null)
				req.setMaxSilenceSeconds(maxSilence);
			if (dtmf != null && !dtmf.isEmpty())
				req.setTerminateOn(dtmf);
			if (startHandler != null)
				arity.addGeneralEventHandler(RecordingStarted.class, recordingStartedHandler(arity));
			return req;
		}

		public ChannelsRecordPostRequest build(ChannelsRecordPostRequest req, ARIty arity) {
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
			if (startHandler != null)
				arity.addGeneralEventHandler(RecordingStarted.class, recordingStartedHandler(arity));
			return req;
		}

		public Builder onStart(Consumer<AsteriskRecording> handler) {
			this.startHandler.set(handler);
			return this;
		}
		
		private BiConsumer<RecordingStarted, EventHandler<RecordingStarted>> recordingStartedHandler(ARIty arity) {
			return (rs,se) -> {
				if (!Objects.equals(rs.getRecording().getName(), filename) || startHandler.get() == null) return;
				se.unregister();
				Consumer<AsteriskRecording> h = startHandler.getAndSet(null);
				h.accept(new AsteriskRecording(arity, rs.getRecording()));
			};
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
		return rec.getDuration() != null ? rec.getDuration() : 0;
	}

	public int getTalkingDuration() {
		return rec.getTalking_duration() != null ? rec.getTalking_duration() : 0;
	}

	public int getSilenceDuration() {
		return rec.getSilence_duration() != null ? rec.getSilence_duration() : 0;
	}

	public CompletableFuture<AsteriskRecording> waitUntilEnd() {
		CompletableFuture<AsteriskRecording> waitForDone = new CompletableFuture<>();
		arity.addGeneralEventHandler(RecordingFinished.class, (e,se) -> {
			if (!e.getRecording().getName().equals(rec.getName())) return;
			se.unregister();
			storedRecording.setLiveRecording(rec = e.getRecording());
			log.debug("Recording finished: {}", this);
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
	
	@Override
	public String toString() {
		return rec.getName() + ":" + rec.getState() + ":" + rec.getCause() + ":" + rec.getFormat() + ":" + rec.getDuration() + "s";
	}

}
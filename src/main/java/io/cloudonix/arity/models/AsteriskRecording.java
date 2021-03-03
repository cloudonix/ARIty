package io.cloudonix.arity.models;

import java.util.UUID;
import java.util.function.Consumer;

import ch.loway.oss.ari4java.generated.actions.requests.BridgesRecordPostRequest;
import ch.loway.oss.ari4java.generated.actions.requests.ChannelsRecordPostRequest;
import ch.loway.oss.ari4java.generated.models.LiveRecording;
import io.cloudonix.arity.ARIty;

public class AsteriskRecording {

	private ARIty arity;
	private LiveRecording rec;

	public AsteriskRecording(ARIty arity, LiveRecording rec) {
		this.arity = arity;
		this.rec = rec;
		
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

	public static Builder build(Consumer<Builder> withBuilder) {
		Builder builder = new Builder();
		withBuilder.accept(builder);
		return builder;
	}
}
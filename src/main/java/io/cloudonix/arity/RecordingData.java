package io.cloudonix.arity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import ch.loway.oss.ari4java.generated.models.LiveRecording;
import ch.loway.oss.ari4java.generated.models.StoredRecording;

/**
 * Access to recording information, whether that recording is live or already stored.
 *
 * @author naamag
 * @author odeda
 */
public class RecordingData {
	
	private String recordingName;
	private LiveRecording recording;
	private Instant startingTime;
	private ARIty arity;
	transient private StoredRecording stored;

	RecordingData(ARIty arity, String recordingName, Instant startingTime) {
		this.arity = arity;
		this.recordingName = recordingName;
		this.startingTime = startingTime;
	}
	
	void setLiveRecording(LiveRecording rec) {
		recording = Objects.requireNonNull(rec);
		try {
			if (recording.getDuration() > 0)
				return;
		} catch (UnsupportedOperationException e) {
			return; // don't try to update duration if its not supported
		} catch (NullPointerException e) { // could be caused if server didn't send duration, calc here anyawy
		}
		// update duration if it wasn't sent correctly
		recording.setDuration((int) Duration.between(startingTime,  Instant.now()).getSeconds());
	}

	public String getRecordingName() {
		return recordingName;
	}

	public LiveRecording getRecording() {
		return recording;
	}

	public Instant getStartingTime() {
		return startingTime;
	}
	
	public CompletableFuture<StoredRecording> getStoredRecording() {
		if (Objects.nonNull(stored))
			return CompletableFuture.completedFuture(stored);
		return Operation.<StoredRecording>retry(cb -> arity.getAri().recordings().getStored(recordingName).execute(cb))
				.thenApply(s -> stored = s);
	}

}

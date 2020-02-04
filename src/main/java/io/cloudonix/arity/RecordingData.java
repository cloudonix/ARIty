package io.cloudonix.arity;

import java.time.Instant;
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

	RecordingData(ARIty arity, String recordingName, LiveRecording recording, Instant startingTime) {
		this.arity = arity;
		this.recordingName = recordingName;
		this.recording = recording;
		this.startingTime = startingTime;
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
		return Operation.retry(cb -> arity.getAri().recordings().getStored(recordingName).execute(cb));
	}

}

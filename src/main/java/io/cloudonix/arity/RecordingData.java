package io.cloudonix.arity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.models.LiveRecording;
import ch.loway.oss.ari4java.generated.models.StoredRecording;

/**
 * Access to recording information, whether that recording is live or already stored.
 *
 * @author naamag
 * @author odeda
 */
public class RecordingData {
	
	private static final Logger log = LoggerFactory.getLogger(RecordingData.class);
	
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
	
	public RecordingData(ARIty arity, String name) {
		this(arity, name, Instant.now());
	}

	public void setLiveRecording(LiveRecording rec) {
		recording = Objects.requireNonNull(rec);
		try {
			if (recording.getDuration() > 0)
				return;
		} catch (UnsupportedOperationException e) {
			log.error("Error getting live recording duration", e);
			return; // don't try to update duration if its not supported
		} catch (NullPointerException e) { // could be caused if server didn't send duration, calc here anyawy
			log.warn("Server provided no duration value for live recording");
		}
		// update duration if it wasn't sent correctly
		recording.setDuration((int) Duration.between(startingTime,  Instant.now()).getSeconds());
	}

	public String getRecordingName() {
		return recordingName;
	}
	
	public boolean hasRecording() {
		return Objects.nonNull(recording);
	}

	public LiveRecording getRecording() {
		return recording;
	}

	public Instant getStartingTime() {
		return startingTime;
	}
	
	@SuppressWarnings("deprecation")
	public CompletableFuture<StoredRecording> getStoredRecording() {
		if (Objects.nonNull(stored))
			return CompletableFuture.completedFuture(stored);
		return Operation.<StoredRecording>retry(cb -> arity.getAri().recordings().getStored(recordingName).execute(cb))
				.thenApply(s -> stored = s);
	}

	AtomicReference<byte[]> recordingCache = new AtomicReference<>();
	@SuppressWarnings("deprecation")
	public CompletableFuture<byte[]> getStoredRecordingData() {
		if (recordingCache.get() != null)
			return CompletableFuture.completedFuture(recordingCache.get());
		return Operation.<byte[]>retry(cb -> arity.getAri().recordings().getStoredFile(recordingName).execute(cb))
				.whenComplete((data, t) -> {
					if (data != null && data.length > 0)
						recordingCache.compareAndExchange(null, data);
				});
	}
	
	@SuppressWarnings("deprecation")
	public CompletableFuture<Void> deleteRecording() {
		return Operation.retry(cb -> arity.getAri().recordings().deleteStored(recordingName).execute(cb));
	}

	public int getDuration() {
		try {
			return hasRecording() ? recording.getDuration().intValue() : 
				(int)Duration.between(startingTime,  Instant.now()).getSeconds();
		} catch (NullPointerException /* duration not set */ | UnsupportedOperationException /* wrong version */ e) {
			log.error("Error resolving recording duration", e);
			return -1;
		}
	}
	
	@Override
	public String toString() {
		return String.format("RecordingData:%1$s:%2$s:%3$s", recordingName, startingTime,
				stored != null ? "stored" : (recording != null ? "live" : "pending")); 
	}
	
}

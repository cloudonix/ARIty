package io.cloudonix.arity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import ch.loway.oss.ari4java.generated.models.LiveRecording;

/**
 * Class representing data about a recording
 *
 * @author naamag
 *
 */
public class RecordingData {
	private String recordingName;
	private LiveRecording recording;
	private Instant startingTime;

	public RecordingData(String recordingName, LiveRecording recording, Instant startingTime) {
		this.recordingName = recordingName;
		this.recording = recording;
		this.startingTime = startingTime;
	}

	public String getRecordingName() {
		return recordingName;
	}

	public void setRecordingName(String recordingName) {
		this.recordingName = recordingName;
	}

	public LiveRecording getRecording() {
		return recording;
	}

	public void setRecording(LiveRecording recording) {
		this.recording = recording;
	}

	/**
	 * Read recoding start time as a time stamp, such as 2007-12-03T10:15:30
	 * @return
	 */
	public LocalDateTime getStartingTime() {
		return LocalDateTime.parse(startingTime.toString(), DateTimeFormatter.ISO_INSTANT);
	}

	public void setStartingTime(Instant startingTime) {
		this.startingTime = startingTime;
	}
}

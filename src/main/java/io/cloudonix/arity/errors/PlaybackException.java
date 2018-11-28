package io.cloudonix.arity.errors;

/**
 * The class throws an exception when a play back can not be played
 * @author naamag
 *
 */
public class PlaybackException extends Exception {
	
	private static final long serialVersionUID = 1L;
	private String playbackFile = "";
	
	public PlaybackException (String playback, Throwable t) {
		
		super("playback: "+ playback+ "failed", t);
		playbackFile = playback;
	}
	
	public String getPlaybackFileName () {
		return playbackFile;
	}
	
}

package io.cloudonix.arity.errors;

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

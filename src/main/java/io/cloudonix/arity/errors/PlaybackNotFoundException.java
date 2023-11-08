package io.cloudonix.arity.errors;

/**
 * An exception representing the 404 error from the playbacks endpoint
 */
public class PlaybackNotFoundException extends ARItyException {
	private static final long serialVersionUID = 1338424543610715470L;

	public PlaybackNotFoundException(Throwable ariError) {
		super(ariError);
	}

}

package io.cloudonix.arity.errors;

public class PlaybackNotFound extends ARItyException {
	private static final long serialVersionUID = 1338424543610715470L;

	public PlaybackNotFound(Throwable ariError) {
		super(ariError);
	}

}

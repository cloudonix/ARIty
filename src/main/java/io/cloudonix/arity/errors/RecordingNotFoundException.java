package io.cloudonix.arity.errors;

public class RecordingNotFoundException extends ARItyException {

	private static final long serialVersionUID = 1L;

	public RecordingNotFoundException (String name, Throwable t) {
		super("Recording "+name+" not found", t);
	}

}

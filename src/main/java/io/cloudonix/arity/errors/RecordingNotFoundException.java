package io.cloudonix.arity.errors;

/**
 * An exception representing the 404 error from the recordings endpoint 
 */
public class RecordingNotFoundException extends ARItyException {

	private static final long serialVersionUID = 1L;

	public RecordingNotFoundException(Throwable cause) {
		super(cause);
	}
	
	public RecordingNotFoundException(String name, Throwable t) {
		super("Recording "+name+" not found", t);
	}

}

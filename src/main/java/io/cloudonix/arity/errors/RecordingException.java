package io.cloudonix.arity.errors;

/**
 * The class throws an exception when a recording failed
 * @author naamag
 *
 */
public class RecordingException extends ARItyException {

	private static final long serialVersionUID = 1L;

	public RecordingException (String name, Throwable t) {
		super("Recording "+name+" failed: "+ t.getMessage(), t);
	}

	public RecordingException (String name, String message) {
		super("Recording "+name+" failed: "+ message);
	}
	
}

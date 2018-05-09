package io.cloudonix.arity.errors;

import java.util.logging.Logger;

/**
 * The class throws an exception when a recording failed
 * @author naamag
 *
 */
public class RecordingException extends Exception {

	private static final long serialVersionUID = 1L;
	private final static Logger logger = Logger.getLogger(RecordingException.class.getName());

public RecordingException (String name, Throwable t) {
		super(t);
		logger.info("recording "+name+" failed: "+ ErrorStream.fromThrowable(t));
	}
	
}

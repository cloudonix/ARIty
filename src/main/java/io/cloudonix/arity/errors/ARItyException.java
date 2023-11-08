package io.cloudonix.arity.errors;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.loway.oss.ari4java.tools.ARIException;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.Operation;

@SuppressWarnings("serial")
public class ARItyException extends ARIException {

	public ARItyException(Throwable cause) {
		super(cause);
	}

	public ARItyException(String message) {
		super(message);
	}

	public ARItyException(String message, Throwable cause) {
		super(new Exception(message, cause));
	}

	/**
	 * A generic exception mapper for {@link Operation#retry(io.cloudonix.arity.Operation.AriOperation, java.util.function.Function)}
	 * that converts known {@link RestException}s to known ARIty errors (exceptions from the {@code io.cloudonix.arity.errors} package)
	 * 
	 * Use it like so: {@code Operation.retry(cb -> someAri4JavaOperation.execute(cb), ARItyException::ariRestExceptionMapper)}
	 * 
	 * This mechanism isn't meant to replace **all** exception mappers - some provide value by retaining and injecting
	 * additional information to the generated exception to provided needed context (and/or useful error messages).
	 * This utility as a "least effort, just give me something useful" mode that can take advantage of previously defined
	 * exceptions that can provide better context than a plain old RestException with minimal code. 
	 * 
	 * @param ariError the ari4java exception to be mapped
	 * @return an instance of a logical ARItyException, if a matching one is found, null otherwise 
	 */
	public static Exception ariRestExceptionMapper(Throwable ariError) {
		if (!(ariError instanceof RestException))
			return null; // we only map RestExceptions
		RestException err = (RestException) ariError;
		String exception = Stream.of(err.getMessage().trim().split("\\s+"))
				.map(w -> w.substring(0,1).toUpperCase() + w.substring(1).toLowerCase())
				.collect(Collectors.joining());
		try {
			Class<?> exClazz = Class.forName(String.format("%s.%sException", ARItyException.class.getPackageName(), exception));
			return (Exception) exClazz.getConstructor(Throwable.class).newInstance(ariError);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			return null;
		}
	}
	
	/**
	 * Helper utility for exception mappers that must return an exception (to abort retries, or for any other reason
	 * @param ariError the ari4java exception to be mapped
	 * @param defaultSupplier if the autoloading mapper did not find an exception to match, the this supplier will be called
	 *   and its value will be returned (even if it is null itself)
	 * @return an autoloaded ARItyException that was mapped from the ari4java RestException, or the result from the supplier if
	 * no autoloaded exception can be found
	 */
	public static Exception ariRestExceptionMapper(Throwable ariError, Supplier<Exception> defaultSupplier) {
		var err = ariRestExceptionMapper(ariError);
		if (err == null)
			return defaultSupplier.get();
		return err;
	}
}

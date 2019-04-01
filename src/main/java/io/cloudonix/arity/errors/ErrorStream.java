package io.cloudonix.arity.errors;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Iterator;

/**
 * Convert an exception to a readable exception
 * 
 * @author odeda
 *
 */
public class ErrorStream extends PrintStream implements Iterable<String> {

	private StringBuilder data = new StringBuilder();
	private PipedInputStream pip = new PipedInputStream(16384);
	private BufferedReader reader = new BufferedReader(new InputStreamReader(pip));
	private Thread thr;
	private boolean closing = false;

	private ErrorStream(String text) {
		super(new PipedOutputStream());
		data.append(text);
	}

	public ErrorStream() throws IOException {
		super(new PipedOutputStream());
		((PipedOutputStream) out).connect(pip);
		(thr = new Thread(() -> {
			try {
				String line;
				while (Objects.nonNull(line = reader.readLine()))
					data.append(line).append('\n');
			} catch (IOException e) {
			}
		}, "ErrorStream Collector")).start();
	}

	@Override
	public void close() {
		if (closing)
			return;
		closing = true;
		flush();
		super.close();
		try {
			thr.join();
		} catch (InterruptedException e) {
		}
	}

	public String toString() {
		return data.toString();
	}

	public Iterator<String> iterator() {
		return Arrays.asList(data.toString().split("\n")).iterator();
	}

	/**
	 * helper method to print the error in a readable way
	 * 
	 * @param failure the error (throwable)
	 * @return
	 */
	public static ErrorStream fromThrowable(Throwable failure) {
		try {
			ErrorStream es = new ErrorStream();
			failure.printStackTrace(es);
			es.close();
			return es;
		} catch (IOException e) {
			return new ErrorStream("Error converting throwable to Errorstream: " + e);
		}
	}

}

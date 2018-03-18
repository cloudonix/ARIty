package io.cloudonix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ARItySipInitiator {

	public ARItySipInitiator() {
	}

	private final static Logger logger = Logger.getLogger(ARItySipInitiator.class.getName());

	//private static String queueUrl = null;
	private static LinkedList<Integer> availblePorts = fillPortList();
	private static LinkedList<ARItySipLayer> sipLayersInUse = new LinkedList<ARItySipLayer>();


	private String getInstanceId() throws IOException {
		try {
			logger.entering(this.getClass().getName(), "getInstanceId");
			while (true) {
				try {
					return getResult(new URL("http://169.254.169.254/latest/meta-data/instance-id").getContent());
				} catch (MalformedURLException e) {
					e.printStackTrace();
					throw new IOException(e);
				} catch (ConnectException e) {
					logger.warning("Retrying getInstanceId because of: " + e.getMessage());
				}
			}
		} finally {
			logger.exiting(this.getClass().getName(), "getInstanceId");
		}
	}

	private String getResult(Object obj) {
		if (obj instanceof InputStream) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader((InputStream) obj, "UTF-8"))) {
				return br.lines().collect(Collectors.joining());
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		return obj.toString();
	}

	private static LinkedList<Integer> fillPortList() {
		LinkedList<Integer> ports = new LinkedList<Integer>();
		for (int i = 5300; i > 5060; i--) {
			ports.add(i);
		}
		return ports;
	}

	public static CompletableFuture<Void> call(String ipTo,String ipFrom, String dnid) throws Exception {
		logger.setLevel(Level.FINER);
		logger.info("Started SipInitiator");

		ARItySipLayer newSipLayer = new ARItySipLayer("cloudonix", ipFrom, 5061);
		return newSipLayer.sendInvite(dnid, ipTo, "token1234");
		// to shutdown the stack- ask Tal!

	}
}

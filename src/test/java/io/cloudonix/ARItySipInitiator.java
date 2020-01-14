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

import webphone.webphone;

public class ARItySipInitiator {

	public ARItySipInitiator() {
	}

	private final static Logger logger = Logger.getLogger(ARItySipInitiator.class.getName());

	//private static String queueUrl = null;
	private static LinkedList<Integer> availablePorts = fillPortList();
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

	public static CompletableFuture<Void> call(String address, String ipFrom, String dnid) throws Exception {
		logger.setLevel(Level.FINER);
		logger.info("Started SipInitiator");

		webphone wobj = new webphone();
		wobj.API_SetParameter("serveraddress", address);
//		wobj.API_SetParameter("username", "usertest");
//		wobj.API_SetParameter("password", "123");
		wobj.API_SetParameter("register", "0");
		wobj.API_SetParameter("hasgui", "false");
		wobj.API_SetParameter("loglevel", "2");
		wobj.API_SetParameter("logtoconsole", "true");
		wobj.API_SetParameter("events", "3");
		wobj.API_SetParameter("canlogtofile", "false");

		wobj.API_Start();
		logger.info("SIP stack started");

		wobj.API_Call(-1, dnid);
		logger.info("Sent INVITE");
		return waitForCallEnd(wobj)
				.thenAccept(v -> {
					logger.info("Done with call");
					wobj.API_Stop();
					wobj.Terminate();
				});

//		ARItySipLayer newSipLayer = new ARItySipLayer("cloudonix", ipFrom, availablePorts.removeFirst());
//		return newSipLayer.sendInvite(dnid, address, "token1234");
		// to shutdown the stack- ask Tal!
	}

	private static CompletableFuture<Void> waitForCallEnd(webphone wobj) {
		return CompletableFuture.runAsync(() -> {
			while (true) {
				// get the notifications from the SIP stack
				String sipnotifications = wobj.API_GetNotificationsSync();
				if (sipnotifications != null && sipnotifications.length() > 0) {
					// split by line
					String[] notarray = sipnotifications.split("\r\n");
					if (notarray == null || notarray.length < 1) {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} // some error occured. sleep a bit just to be sure to avoid busy loop
					} else {
						for (int i = 0; i < notarray.length; i++) {
							if (notarray[i] != null && notarray[i].length() > 0) {
								if (ProcessNotifications(notarray[i]))
									return;
							}
						}
					}
				} else {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} // some error occured. sleep a bit just to be sure to avoid busy loop
				}
			}
		});
	}

	private static boolean ProcessNotifications(String msg) {
		logger.info("Got message: '" + msg + "'");
		String[] parts = msg.split(",");
		return (parts.length > 2 && "Call finished".equalsIgnoreCase(parts[2]));
	}
}

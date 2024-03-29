package io.cloudonix;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.github.dockerjava.api.DockerClient;

import webphone.webphone;

public class ARItySipInitiator {

	private static final String WEBPHONE_BASE_IMAGE = "openjdk:11-jre";
	static Future<String> webphoneImage = buildWebPhone();

	public static class XVFBContainer extends GenericContainer<XVFBContainer> {
		XVFBContainer() {
			super("misoca/xvfb");
			withNetworkAliases("xvfb");
		}
	}

	public static class WebphoneContainer extends GenericContainer<WebphoneContainer> {
		private XVFBContainer xvfb;
		boolean calldone = false, needreport = false;
		int lastStatus;
		Pattern statusCatcher = Pattern.compile("SIP/\\d\\.\\d (\\d+) \\w+");
		private boolean testDebug = System.getProperty("io.cloudonix.arity.jvoip.debug", "false").equalsIgnoreCase("true");

		@SuppressWarnings("resource")
		WebphoneContainer(String address, String destination) {
			super(webphoneImage);
			xvfb = new XVFBContainer().withExposedPorts(6001);
			xvfb.start();
			logger().info("Started XVFB");
			withEnv("DISPLAY", xvfb.getContainerInfo().getNetworkSettings().getNetworks().entrySet()
					.stream().map(e -> e.getValue().getIpAddress()).filter(Objects::nonNull)
					.findFirst().orElseThrow(RuntimeException::new) + ":1");
			withCommand("/app/jvoip.sh", address, destination);
			withLogConsumer(log -> logger.debug(log.getUtf8String()));
		}
		@Override
		public void start() {
			super.start();
			logger().info("Started JVoiP");
			followOutput(output->{
				String line = output.getUtf8String().replaceAll("\n$", "");
				if (line.contains("] SEND,") || line.contains("] REC,"))
					needreport = true;
				else if (line.contains("[mt:"))
					needreport = false;
				if (needreport && testDebug) {
					logger().info(line);
				} else {
					logger().trace(line);
				}
				Matcher m = statusCatcher.matcher(line);
				if (m.find())
					lastStatus = Integer.valueOf(m.group(1));
				synchronized (this) {
					if (line.contains("call done") || line.contains("EVENT,STATUS,1,Finished,")) {
						calldone = true;
						notify();
					}
				}
			});
		}
		public int getFinalStatus() {
			synchronized (this) {
				while (!calldone)
					try {
						wait();
					} catch (InterruptedException e) {
					}
			}
			return lastStatus;
		}
		@Override
		public void stop() {
			super.stop();
			xvfb.stop();
		}
	}

	private final static Logger logger = LoggerFactory.getLogger(ARItySipInitiator.class);

	static {
		try {
			// make sure the huge openjdk image is loaded before we starting counting time
			webphoneImage.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}
	
	
	
	public static void ensureBaseImage() throws InterruptedException {
		DockerClient dockerClient = DockerClientFactory.instance().client();
		dockerClient.pullImageCmd(WEBPHONE_BASE_IMAGE).start().awaitCompletion();
	}
	
	private static Future<String> buildWebPhone() {
		return new ImageFromDockerfile("webphone", false)
		.withFileFromFile("jvoip.jar", new File("repo/jvoip/jvoip/8.4/jvoip-8.4.jar"))
		.withFileFromString("jvoip.sh", "#!/bin/bash -xe\n"+
				"java -jar /app/jvoip.jar serveraddress=\"$1\" callto=\"$2\" \\\n" +
				"	username=usertest password=123 \\\n" +
				"	autocall=true loglevel=5 register=0 hasgui=false logtocnosole=true \\\n" +
				"	events=3 canlogtofile=false iscommandline=true")
		.withDockerfileFromBuilder(b -> b.from(WEBPHONE_BASE_IMAGE)
				.run("apt update && apt install -q -y x11-utils")
				.add("jvoip.jar", "/app/jvoip.jar")
				.add("jvoip.sh", "/app/jvoip.sh")
				.run("chmod a+x /app/jvoip.sh"));
	}

	public static CompletableFuture<Integer> call(String address, String ipFrom, String dnid) throws Exception {
		logger.info("Started SipInitiator");

		return CompletableFuture.supplyAsync(() -> {
			try (WebphoneContainer webphone = new WebphoneContainer(address, dnid)) {
				webphone.start();
				return webphone.getFinalStatus();
			} finally {
				logger.info("Done with call!");
			}
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

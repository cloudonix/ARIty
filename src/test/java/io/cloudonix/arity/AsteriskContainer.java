package io.cloudonix.arity;

import java.util.Map;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.Ports.Binding;

class AsteriskContainer extends GenericContainer<AsteriskContainer> {

	public AsteriskContainer () {
		super("andrius/asterisk:14.x");
		this.addFileSystemBind("src/test/resources/extensions.conf", "/etc/asterisk/extensions.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/http.conf", "/etc/asterisk/http.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/ari.conf", "/etc/asterisk/ari.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/sip.conf", "/etc/asterisk/sip.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/logger.conf", "/etc/asterisk/logger.conf", BindMode.READ_ONLY);
		this.withCreateContainerCmdModifier(c -> c
				.withExposedPorts(new ExposedPort(5060, InternetProtocol.UDP))
				.withExposedPorts(new ExposedPort(8088, InternetProtocol.TCP)));
	}

	@Override
	public void start() {
		Object monitor = new Object();
		super.start();
		followOutput(output->{
			String line = output.getUtf8String().replaceAll("\n$", "");
			logger().debug(line);
			if (line.contains("Asterisk Ready"))
				synchronized (monitor) {
					monitor.notify();
				}
		});
		synchronized (monitor) {
			try {
				monitor.wait();
			} catch (InterruptedException e) {
			}
		}
	}

	public String getAriURL() {
		return "http://"+ this.getContainerIpAddress()+ ":" + this.getMappedPort(8088) + "/";
	}

	public String getSipHostPort() {
		InspectContainerResponse containerInfo = getContainerInfo();
		if (containerInfo == null)
			throw new RuntimeException("No container info!");

		Map<ExposedPort, Binding[]> binding = containerInfo.getNetworkSettings().getPorts().getBindings();
		Binding[] port = binding.get(new ExposedPort(5060, InternetProtocol.UDP));
		if (port == null || port.length <= 0)
			throw new RuntimeException("No ports!");

		int mappedPort = Integer.valueOf(port[0].getHostPortSpec());

		logger().info("Using SIP mapped address " + this.getContainerIpAddress()+ ":" + mappedPort);
		return this.getContainerIpAddress()+ ":" + mappedPort;
	}

}
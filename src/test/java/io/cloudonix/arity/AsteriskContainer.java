package io.cloudonix.arity;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.InternetProtocol;

class AsteriskContainer extends GenericContainer<AsteriskContainer> {
	
	public AsteriskContainer () {
		super("registry.gitlab.com/cloudonix/docker/asterisk-docker:14");
		this.addFileSystemBind("src/test/resources/extensions.conf", "/etc/asterisk/extensions.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/http.conf", "/etc/asterisk/http.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/ari.conf", "/etc/asterisk/ari.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/sip.conf", "/etc/asterisk/sip.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/logger.conf", "/etc/asterisk/logger.conf", BindMode.READ_ONLY);
		this.addExposedPorts(8088);
		this.withCreateContainerCmdModifier(c-> c.withExposedPorts(new ExposedPort(5060, InternetProtocol.UDP)));
		this.followOutput(output->{
			logger().info(output.getUtf8String());
		});
	}
	
	public String getAriURL () {
		return "http://"+ this.getContainerIpAddress()+ ":" + this.getMappedPort(8088);
	}
	
	public String getSipServerURL () {
		return "http://"+this.getContainerIpAddress()+ ":" + this.getMappedPort(5060);
	}
	
}
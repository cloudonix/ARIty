package io.cloudonix.arity;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

class AsteriskContainer extends GenericContainer<AsteriskContainer> {
	
	public AsteriskContainer () {
		super("registry.gitlab.com/cloudonix/docker/asterisk-docker:14");
		this.addFileSystemBind("src/test/resources/extensions.conf", "/etc/asterisk/extensions.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/http.conf", "/etc/asterisk/http.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/ari.conf", "/etc/asterisk/ari.conf", BindMode.READ_ONLY);
		this.addFileSystemBind("src/test/resources/sip.conf", "/etc/asterisk/sip.conf", BindMode.READ_ONLY);
		this.addExposedPorts(8088,5060);
	}
	
	public String getAriIP () {
		return "http://"+ this.getTestHostIpAddress()+ ":" + this.getMappedPort(8088);
	}
	
	public String getSipServerIP () {
		return "http://"+this.getTestHostIpAddress()+ ":" + this.getMappedPort(5060);
	}
	
}
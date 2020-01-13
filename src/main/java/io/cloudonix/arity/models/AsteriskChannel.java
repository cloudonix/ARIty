package io.cloudonix.arity.models;

import ch.loway.oss.ari4java.generated.models.Channel;
import io.cloudonix.arity.ARIty;

public class AsteriskChannel {

	private ARIty arity;
	private Channel channel;

	public AsteriskChannel(ARIty arity, Channel channel) {
		this.arity = arity;
		this.channel = channel;
	}

}

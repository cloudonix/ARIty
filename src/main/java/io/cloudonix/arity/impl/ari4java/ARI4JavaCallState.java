package io.cloudonix.arity.impl.ari4java;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.models.Channel;
import io.cloudonix.arity.ARIty;

public class ARI4JavaCallState extends io.cloudonix.arity.CallState {

	private ARI ari;
	private ARIty arity;
	private Channel channel;

	public ARI4JavaCallState(Channel channel, ARIty arity, ARI ari) {
		super(channel.getId(), channel.getName(), channel.getState(), arity);
		this.ari = ari;
		this.arity = arity;
		this.channel = channel;
	}

}

package io.cloudonix.arity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import io.cloudonix.arity.errors.RedirectException;
import io.cloudonix.lib.Futures;

/**
 * class that executes channel 'redirect' operation
 * 
 * @author naamag
 *
 */
public class Redirect extends Operation {

	private String endpoint;
	private final static Logger logger = Logger.getLogger(Redirect.class.getName());

	public Redirect(String channelId, ARIty arity, ARI ari,String endpoint) {
		super(channelId, arity);
		this.endpoint = endpoint;
	}

	@Override
	public CompletableFuture<Redirect> run() {
		if(Objects.isNull(endpoint)) {
			logger.warning("The endpoint to redirect the channel to is not given! abort redirect");
			return Futures.failedFuture(new RedirectException("Endpoint can not be null!"));
		}
		logger.info("Now redirecting... channel id: "+getChannelId()+" , to: "+endpoint);
		return Operation.<Void>retryOperation(cb->getArity().getAri().channels().redirect(getChannelId(), endpoint,cb))
				.thenApply(v->null);
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

}

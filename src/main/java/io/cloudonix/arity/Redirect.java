package io.cloudonix.arity;

import static java.util.concurrent.CompletableFuture.failedFuture;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudonix.arity.errors.RedirectException;

/**
 * Channel redirection operation
 *
 * @author naamag
 */
public class Redirect extends Operation {

	private String endpoint;
	private final static Logger logger = LoggerFactory.getLogger(Redirect.class);

	public Redirect(String channelId, ARIty arity, String endpoint) {
		super(channelId, arity);
		this.endpoint = endpoint;
	}

	@SuppressWarnings("deprecation")
	@Override
	public CompletableFuture<Redirect> run() {
		if(Objects.isNull(endpoint)) {
			logger.warn("The endpoint to redirect the channel to is not given! abort redirect");
			return failedFuture(new RedirectException("Endpoint can not be null!"));
		}
		logger.info("Now redirecting... channel id: "+getChannelId()+" , to: "+endpoint);
		return this.<Void>retryOperation(cb->getArity().getAri().channels().redirect(getChannelId(), endpoint).execute(cb))
				.thenApply(v->null);
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

}

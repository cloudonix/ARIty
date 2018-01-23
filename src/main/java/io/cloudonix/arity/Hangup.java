package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.HangUpException;

public class Hangup extends Operation{
	private CompletableFuture<Hangup> compFuture;
	private final static Logger logger = Logger.getLogger(Hangup.class.getName());
	
	/**
	 * Constructor
	 * @param call
	 */
	public Hangup(Call call) {
		super(call.getChannelID(), call.getService(), call.getAri());
		compFuture = new CompletableFuture<>();
	}
	
	/**
	 * The method hangs up a call
	 * 
	 * @return
	 */
	public CompletableFuture<Hangup> run (){
		try {
			// hang up the call
			getAri().channels().hangup(getChanneLID(), "normal");
		} catch (RestException e) {
			logger.severe("failed hang up the call");
			compFuture.completeExceptionally(new HangUpException(e));
			return compFuture;
		}
		return CompletableFuture.completedFuture(this);
		
	}

}

package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.HangUpException;

/**
 * The class represents the Hang up operation (hangs up a call)
 * @author naamag
 *
 */
public class Hangup extends Operation{
	
	private final static Logger logger = Logger.getLogger(Hangup.class.getName());
	
	/**
	 * Constructor
	 * @param callController instance that represents a call
	 */
	public Hangup(CallController callController) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
	}
	
	/**
	 * The method hangs up a call
	 * 
	 * @return
	 */
	public CompletableFuture<Hangup> run (){
		try {
			// hang up the call
			getAri().channels().hangup(getChannelId(), "normal");
		} catch (RestException e) {
			logger.warning("Failed hang up the call");
			return completedExceptionally(new HangUpException(e));
		}
		logger.info("Hanged up call");

		return CompletableFuture.completedFuture(this);
		
	}

}

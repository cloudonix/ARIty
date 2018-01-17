package io.cloudonix.arity;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;

public class Gather extends Verb {
	private CompletableFuture<String> compFuture;
	private String gatherAll;
	private Call call;
	private final static Logger logger = Logger.getLogger(HangUp.class.getName());
	private List<Verb> nestedVerbs;
	private String terminatingKey;

	/**
	 * Constructor
	 * @param call
	 */
	public Gather(Call call) {
		
		super(call.getChannelID(), call.getService(), call.getAri());
		compFuture = new CompletableFuture<>();
		gatherAll = "";
		this.call = call;
	}
	

	/**
	 * Constructor that will be used to support nested verbs
	 * 
	 * @param call
	 * @param terminating key
	 * @param verbs (list of nested verbs to be executed)
	 */
	
	public Gather(Call call, String terminatingKey, List<Verb> verbs) {

		super(call.getChannelID(), call.getService(), call.getAri());
		compFuture = new CompletableFuture<>();
		gatherAll = "";
		this.call = call;
		nestedVerbs = verbs;
		this.terminatingKey = terminatingKey;
	}
	

	/**
	 * The method gathers input from the user
	 * 
	 * @param terminatingKey
	 *            - the digit that stops the gathering
	 * @return
	 */
	public CompletableFuture<String> run(String terminatingKey) {

		// CompletablePlayback playback = new Play(call).runSound(times, soundLocation);

		// stops the playback
		// getAri().playbacks().stop(playback.get().getId());

		getService().addFutureEvent(ChannelDtmfReceived.class, dtmf -> {
			if (!(dtmf.getChannel().getId().equals(getChanneLID()))) {
				logger.info("channel id of dtmf is not the same as the channel id");
				return false;
			}

			logger.info("dtmf channel id is the same as the channel id");
			// if the input is the terminating key "#" then stop
			if (dtmf.getDigit().equals(terminatingKey)) {
				logger.info("the input is: " + terminatingKey);
				logger.info("all input: " + gatherAll);
				compFuture.complete(gatherAll);
				return true;

			}
			// the input is 0-9 or A-E or * - save it with the previous digits
			gatherAll = gatherAll + dtmf.getDigit();
			return false;

		});

		return compFuture;
	}

}

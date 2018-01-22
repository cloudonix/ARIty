package io.cloudonix.arity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.ChannelDtmfReceived;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.PlaybackException;

public class Gather extends Operation {
	private CompletableFuture<Gather> compFuture;
	private String gatherAll;
	private Call call;
	private final static Logger logger = Logger.getLogger(Gather.class.getName());
	private List<CancelableOperations> nestedOperations;
	private String terminatingKey;
	private CancelableOperations currOpertation = null;

	/**
	 * Constructor
	 * 
	 * @param call
	 */
	public Gather(Call call, String termKey) {

		super(call.getChannelID(), call.getService(), call.getAri());
		compFuture = new CompletableFuture<>();
		gatherAll = "";
		this.call = call;
		terminatingKey = termKey;
		nestedOperations = new ArrayList<>();
	}

	/**
	 * Constructor
	 * 
	 * @param call
	 */
	public Gather(Call call) {

		super(call.getChannelID(), call.getService(), call.getAri());
		compFuture = new CompletableFuture<>();
		gatherAll = "";
		this.call = call;
		terminatingKey = "#";
		nestedOperations = new ArrayList<>();
	}

	/**
	 * Constructor that will be used to support nested verbs
	 * 
	 * @param call
	 * @param terminating
	 *            key
	 * @param verbs
	 *            (list of nested verbs to be executed)
	 *//*
		 * 
		 * public Gather(Call call, String terminatingKey, List<Verb> verbs) {
		 * 
		 * super(call.getChannelID(), call.getService(), call.getAri()); compFuture =
		 * new CompletableFuture<>(); gatherAll = ""; this.call = call; nestedVerbs =
		 * verbs; this.terminatingKey = terminatingKey; }
		 */

	/**
	 * The method gathers input from the user
	 * 
	 * @param terminatingKey
	 *            - the digit that stops the gathering
	 * @return
	 */
	public CompletableFuture<? extends Operation> run() {
		
		// if the list of verbs is not empty, execute them one by one- wait until the
		// current verb is finished before continuing to the next on (the completable
		// future is completed )
		if (!nestedOperations.isEmpty()) {
			logger.info("there are verbs in the nested verb list");

			CompletableFuture<? extends Operation> future = nestedOperations.get(0).run();
			currOpertation = nestedOperations.get(0);

			if (nestedOperations.size() > 1 && Objects.nonNull(future)) {
				for (int i = 1; i < nestedOperations.size() && Objects.nonNull(future); i++) {
					future = loopVerbs(future, nestedOperations.get(i));
				}
			}

		}

		getService().addFutureEvent(ChannelDtmfReceived.class, dtmf -> {
			if (!(dtmf.getChannel().getId().equals(getChanneLID()))) {
				logger.info("channel id of dtmf is not the same as the channel id");
				return false;
			}

			logger.info("dtmf channel id is the same as the channel id");
			// if the input is the terminating key "#" then stop
			if (dtmf.getDigit().equals(terminatingKey)) {
				logger.info("all input: " + gatherAll);
				
				// cancel the current operation because the gather is finished
				currOpertation.cancel();
				compFuture.complete(this);
				return true;

			}
			// the input is 0-9 or A-E or * - save it with the previous digits
			gatherAll = gatherAll + dtmf.getDigit();
			return false;

		});

		return compFuture;
	}


	/**
	 * compose the previous future (of the previous verb) to the result of the new future
	 * @param future
	 * @param operation
	 * @return
	 */
	private CompletableFuture<? extends Operation> loopVerbs(CompletableFuture<? extends Operation> future, Operation operation) {
		logger.info("The current nested operation is: " + currOpertation.toString());
		return future.thenCompose(pb -> {
			if (Objects.nonNull(pb))
				// if the previous future (previous playback) is not null, run it
				return operation.run();
			return CompletableFuture.completedFuture(null);
		});
	}

	/**
	 * add new verb to list of nested verbs that run method will execute one by one
	 * 
	 * @param operation
	 * @return
	 */

	public Gather and(CancelableOperations operation) {
		nestedOperations.add(operation);
		return this;
	}

	/**
	 * set the terminating key
	 * 
	 * @param termKey
	 * @return
	 */
	public Gather termKey(String termKey) {
		terminatingKey = termKey;
		return this;
	}

	/**
	 * return all entire input that was gathered
	 * 
	 * @return
	 */
	public String allInputGathered() {
		return gatherAll;
	}

}

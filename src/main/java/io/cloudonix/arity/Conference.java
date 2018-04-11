package io.cloudonix.arity;

import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.generated.Bridge;
import ch.loway.oss.ari4java.generated.ari_2_0_0.models.ChannelEnteredBridge_impl_ari_2_0_0;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ErrorStream;

public class Conference extends Operation {

	private CompletableFuture<Conference> compFuture;
	private String endPointNumber;
	private String endPointChannelId;
	private final static Logger logger = Logger.getLogger(Conference.class.getName());
	private ConcurrentHashMap<Bridge, Integer> bridges;
	private String bridgeName;
	private Bridge currBridge;

	/**
	 * Constructor
	 * 
	 * @param callController
	 *            an instance that represents a call
	 * @param number
	 *            the number we are calling to (the endpoint)
	 */
	public Conference(CallController callController, ConcurrentHashMap<Bridge, Integer> confBridge, String bn) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		compFuture = new CompletableFuture<>();
		bridges = confBridge;
		bridgeName = bn;
	}

	@Override
	public CompletableFuture<? extends Operation> run() {
		//find the conference bridge
		Enumeration<Bridge> confBriges = bridges.keys();
		 currBridge = confBriges.nextElement();
		boolean found = false;
		while (confBriges.hasMoreElements()) {
			if (Objects.equals(currBridge.getName(), bridgeName)) {
				found = true;
				break;
			}
			else
				currBridge = confBriges.nextElement();
		}
		if (!found)
			return null;
		// conference must have at least 2 participants
		if (bridges.get(currBridge).intValue() < 2)
			return null;

		return this.<Void>toFuture(v -> {
			try {
				getAri().bridges().addChannel(currBridge.getId(), getChannelId(), "joining conference");
			} catch (RestException e) {
				logger.info("unable to join channel to the conference bridge " + ErrorStream.fromThrowable(e));
			}
		}).thenAccept(v -> {
			getArity().addFutureEvent(ChannelEnteredBridge_impl_ari_2_0_0.class, (channel) -> {
				if (Objects.equals(currBridge, channel.getBridge())) {
					logger.info(" channel was added to the conference bridge. Channel id is: " + getChannelId());
					compFuture.complete(this);
					return true;
				} else {
					logger.info("failed in adding channel to the conference");
					return false;
				}
			});
			logger.info("future event of ChannelEnteredBridge was added");
		}).thenCompose(v -> compFuture);

	}
}

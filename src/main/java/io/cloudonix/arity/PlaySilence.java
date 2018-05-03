package io.cloudonix.arity;

import java.util.concurrent.CompletableFuture;

/**
 * play a silence file to a channel
 * @author naamag
 *
 */
public class PlaySilence extends Operation{
	
	private CallController callController;
	private String silenceFile;
	
	public PlaySilence(CallController callController, String silenceFile) {
		super(callController.getChannelID(), callController.getARItyService(), callController.getAri());
		this.callController = callController;
		this.silenceFile = silenceFile;
	}

	@Override
	public CompletableFuture<Play> run() {
		return new Play(callController, silenceFile).run();
	}

}

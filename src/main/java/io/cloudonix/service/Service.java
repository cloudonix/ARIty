package io.cloudonix.service;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriFactory;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.Message;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.tools.ARIException;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.service.errors.ConnectionFailedException;

/**
 * The class represents the creation of ari and websocket service that handles
 * the incoming events
 * 
 * @author naamag
 *
 */
public class Service implements AriCallback<Message> {

	private final static Logger logger = Logger.getLogger(Service.class.getName());
	// List of future events
	private CopyOnWriteArrayList<Function<Message, Boolean>> futureEvents = new CopyOnWriteArrayList<>();

	private ARI ari;
	private String appName;
	private Consumer<Call> voiceApp;
	private List<String> ssIdLst = new ArrayList<>();

	public Service(String uri, String name, String login, String pass)throws ConnectionFailedException, URISyntaxException {
		appName = name;

		try {
			ari = AriFactory.nettyHttp(uri, login, pass, AriVersion.ARI_2_0_0);
			logger.info("ari created");
			ari.events().eventWebsocket(appName, true, this);
			logger.info("websocket is open");

		} catch (ARIException e) {
			logger.severe("connection failed: " + e.getMessage());
			throw new ConnectionFailedException(e);
		}
	}

	/**
	 * The method register a new application to be executed
	 * 
	 * @param voiceApp
	 */
	public void registerVoiceApp(Consumer<Call> voiceApp) {
		this.voiceApp = voiceApp;
	}

	@Override
	public void onSuccess(Message event) {
		logger.info(event.toString());
		
		if (event instanceof StasisStart) {
			// StasisStart case
			StasisStart ss = (StasisStart) event;
			// if the list contains the stasis start event with this channel id, ignore it
			if(ssIdLst.contains(ss.getChannel().getId()))
				return;
			logger.info("Channel id of new ss: "+ ss.getChannel().getId());
			Call call = new Call((StasisStart) event, ari, this);
			logger.info("New call created! " + call);
			// save the the channel id of the first stasis
			ssIdLst.add(ss.getChannel().getId());
			voiceApp.accept(call);
		}

		// look for a future event in the event list
		Iterator<Function<Message, Boolean>> itr = futureEvents.iterator();

		while (itr.hasNext()) {
			Function<Message, Boolean> currEntry = itr.next();
			if (currEntry.apply(event)) {
				// remove from the list of future events
				itr.remove();
				logger.info("future event was removed");
				break;
			}
		}

	}

	@Override
	public void onFailure(RestException e) {
		// e.printStackTrace();
		logger.warning(e.getMessage());
	}

	/**
	 * The method handles adding a future event from a specific class (event) to the
	 * future event list
	 * 
	 * @param class1-
	 *            class of the finished event (example: PlaybackFinished)
	 * @param func-
	 *            function to be executed
	 */
	protected <T> void addFutureEvent(Class<T> class1, Function<T, Boolean> func) {

		@SuppressWarnings("unchecked")
		Function<Message, Boolean> futureEvent = (Message message) -> {

			if (class1.isInstance(message))
				return func.apply((T) message);
			return false;
		};
		
		futureEvents.add(futureEvent);
	}

	public String getAppName() {
		return appName;
	}

}

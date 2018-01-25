package io.cloudonix.arity;

import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
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
import io.cloudonix.arity.errors.ConnectionFailedException;

/**
 * The class represents the creation of ari and websocket service that handles
 * the incoming events
 * 
 * @author naamag
 *
 */
public class ARIty implements AriCallback<Message> {

	private final static Logger logger = Logger.getLogger(ARIty.class.getName());
	private Queue<Function<Message, Boolean>> futureEvents = new ConcurrentLinkedQueue<Function<Message, Boolean>>();
	private ARI ari;
	private String appName;
	private Consumer<Call> voiceApp;
	// save the channel id of new calls (for ignoring another stasis start event, if
	// needed)
	private ConcurrentSkipListSet<String> ignoredChannelIds = new ConcurrentSkipListSet<>();

	public ARIty(String uri, String name, String login, String pass)
			throws ConnectionFailedException, URISyntaxException {
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

		if (event instanceof StasisStart) {
			StasisStart ss = (StasisStart) event;
			// if the list contains the stasis start event with this channel id, remove it
			// and continue
			if (ignoredChannelIds.remove(ss.getChannel().getId())) {
				return;
			}
			logger.info("Channel id of the caller: " + ss.getChannel().getId());
			Call call = new Call((StasisStart) event, ari, this);
			logger.info("New call created! " + call);
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
	 * @param class1 class of the finished event (example: PlaybackFinished)
	 * @param func function to be executed
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

	/**
	 * ignore Stasis start from this channel
	 * 
	 * @param id channel id to ignore
	 */
	public void ignoreChannel(String id) {
		ignoredChannelIds.add(id);
	}
	
	/**
	 * disconnect from the websocket (user's choice if to call it or not)
	 */
	public void disconnect() {

		try {
			ari.closeAction(ari.events());
			logger.info("closing the web socket");
		} catch (ARIException e) {
			logger.info("failed closing the web socket");
			e.printStackTrace();
		}

	}

}

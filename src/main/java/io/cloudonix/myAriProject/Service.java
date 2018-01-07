package io.cloudonix.myAriProject;

import java.net.URISyntaxException;
import java.util.Iterator;
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

public class Service implements AriCallback<Message> {
	
	private final static Logger logger = Logger.getLogger(Service.class.getName());
	//List of future events
	private CopyOnWriteArrayList<Function<Message, Boolean>> futureEvents = new CopyOnWriteArrayList<>();

	private ARI ari;
	private String appName;
	private Consumer<Call> voiceApp;
	
	public Service(String uri, String name, String login, String pass) {
		appName = name;	
		
		try {
			ari =  AriFactory.nettyHttp(uri,login, pass, AriVersion.ARI_2_0_0);
			logger.info("ari created");
			ari.events().eventWebsocket(appName, true, this);
			logger.info("websocket is open");
			
		} catch (ARIException | URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * The method register a new call to be executed
	 * @param voiceApp
	 */
	public void registerVoiceApp (Consumer<Call> voiceApp ) {
		this.voiceApp = voiceApp; 
		
	}
	
	@Override
	public void onSuccess(Message event) {
		if (event instanceof StasisStart) {
			// StasisStart case
			Call call = new Call((StasisStart)event , ari, this);
			logger.info("New call created! "+ call);
			voiceApp.accept(call);
		}

		Iterator<Function<Message, Boolean>> itr = futureEvents.iterator();
		
		// look for a future event
		while (itr.hasNext()) {
			Function<Message, Boolean> currEntry = itr.next();
			if (currEntry.apply(event)) {
				// remove from the list of future events
				logger.info("future event was removed");
				itr.remove();
				break;
			}
		}

	}		

	@Override
	public void onFailure(RestException e) {
		e.printStackTrace();
	}
	
	/**
	 * The method handles a adding a future event from a specific class and add it to the future event list
	 * @param class1
	 * @param func
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
	
	public String getAppName () {
		return appName;
	}
	
}

package io.cloudonix.myAriProject;

import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriFactory;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.Message;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.tools.ARIException;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;

public class Service implements AriCallback<Message> {
	
	private final static Logger logger = Logger.getLogger(Service.class.getName());
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
			
		} catch (ARIException | URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void registerVoiceApp (Consumer<Call> voiceApp ) {
		this.voiceApp = voiceApp; 
		
	}
	
	
	@Override
	public void onSuccess(Message event) {
		if (event instanceof StasisStart) {
			// StasisStart case
			//ss = (StasisStart) event;
			Call call = new Call((StasisStart)event , ari, this);
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
		Function<Message, Boolean> pred = (Message message) -> {

			if (class1.isInstance(message))
				return func.apply((T) message);
			return false;
		};

		futureEvents.add(pred);
	}
	
}

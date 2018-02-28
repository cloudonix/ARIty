package io.cloudonix.arity;

import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.CallerID;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.DialplanCEP;
import ch.loway.oss.ari4java.generated.Message;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.generated.Variable;
import ch.loway.oss.ari4java.tools.ARIException;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import io.cloudonix.arity.errors.ConnectionFailedException;
import io.cloudonix.arity.errors.ErrorStream;

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
	private Supplier<CallController> callSupplier = this::hangupDefault;
	// save the channel id of new calls (for ignoring another stasis start event, if
	// needed)
	private ConcurrentSkipListSet<String> ignoredChannelIds = new ConcurrentSkipListSet<>();
	private Exception lastException = null;
	private Channel callChannel = null;

	/**
	 * Constructor
	 * 
	 * @param uri
	 *            URI
	 * @param name
	 *            name of the stasis application
	 * @param login
	 *            user name
	 * @param pass
	 *            password
	 * 
	 * @throws ConnectionFailedException
	 * @throws URISyntaxException
	 */
	public ARIty(String uri, String name, String login, String pass)
			throws ConnectionFailedException, URISyntaxException {
		appName = name;

		try {
			ari = ARI.build(uri, appName, login, pass,  AriVersion.IM_FEELING_LUCKY);
			//ari = AriFactory.nettyHttp(uri, login, pass, AriVersion.ARI_2_0_0);
			//ari = ARI.build(uri, appName, login, pass,  AriVersion.ARI_2_0_0);
			logger.info("ari created");
			logger.info("ari version: " + ari.getVersion());
			ari.events().eventWebsocket(appName, true, this);
			logger.info("websocket is open");
		} catch (ARIException e) {
			logger.severe("connection failed: " + ErrorStream.fromThrowable(e));
			throw new ConnectionFailedException(e);
		}
	}

	/**
	 * The method register a new application to be executed according to the class
	 * of the voice application
	 * 
	 * @param class
	 *            instance of the class that contains the voice application (extends
	 *            from callController)
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	public void registerVoiceApp(Class<? extends CallController> controllerClass) {
		callSupplier = new Supplier<CallController>() {

			@Override
			public CallController get() {
				try {
					return controllerClass.newInstance();

				} catch (InstantiationException | IllegalAccessException e) {
					lastException = e;
					return hangupDefault();
				}
			}
		};
	}

	/**
	 * The method register the voice application (the supplier that has a
	 * CallController, meaning the application)
	 * 
	 * @param controllorSupplier
	 *            the supplier that has the CallController (the voice application)
	 */
	public void registerVoiceApp(Supplier<CallController> controllorSupplier) {
		if(Objects.isNull(controllorSupplier))
		return;
		
		callSupplier = controllorSupplier;
	}

	/**
	 * The method register the voice application and execute it
	 * 
	 * @param cc
	 */
	public void registerVoiceApp(Consumer<CallController> cc) {
		callSupplier = () -> {
			return new CallController() {

				@Override
				public void run() {
					cc.accept(this);
				}
			};
		};
	}

	/**
	 * The method hangs up the call if we can't create an instance of the class that
	 * contains the voice application
	 * 
	 * @param e
	 * @return
	 */

	protected CallController hangupDefault() {
		return new CallController() {

			public void run() {
				hangup().run();

				if(Objects.isNull(lastException))
					logger.severe("Your Application is not registered!");

				logger.severe("Invalid application!");
			}
		};
	}

	@Override
	public void onSuccess(Message event) {
		
		if (event instanceof StasisStart) {
			logger.info("asterisk id: "+ event.getAsterisk_id());
			
			StasisStart ss = (StasisStart) event;
			callChannel = ss.getChannel();
			// information about the channel:
			CallerID caller = ss.getChannel().getCaller();
			String callerIdName = caller.getName();
			String callerIdNumber = caller.getNumber();
			logger.info("caller name is: "+ callerIdName+ " and caller number is: "+ callerIdNumber);
			logger.info("channel vars are: "+ ss.getChannel().getChannelvars());
			CallerID callerConnected = ss.getChannel().getConnected();
			String callerConnectedName = callerConnected.getName();
			String callerConnectedNumber = callerConnected.getNumber();
			logger.info("caller connected name is: "+ callerConnectedName+ " and caller connected number is: "+ callerConnectedNumber);
			logger.info("channel name is: "+ ss.getChannel().getName());
			logger.info("state is: "+ ss.getChannel().getState());
			logger.info("channel was created at: "+ss.getChannel().getCreationtime());
			logger.info("channel id: "+ ss.getChannel().getId());
			DialplanCEP dialplanINfo = ss.getChannel().getDialplan();
			logger.info("context in dialplan: "+ dialplanINfo.getContext());
			logger.info("extension in dialplan is: "+ dialplanINfo.getExten());
			logger.info("priority: "+ dialplanINfo.getPriority());
			
			try {
				logger.info("header from: "+ ari.channels().getChannelVar(ss.getChannel().getId(), "SIP_HEADER(FROM)").getValue());
			} catch (RestException e) {
				logger.severe("unable to find the header");
			}
			logger.info("----------------------------------------------------------------------------------------------------------");
			// if the list contains the stasis start event with this channel id, remove it
			// and continue
			if (ignoredChannelIds.remove(ss.getChannel().getId())) {
				return;
			}
			logger.info("Channel id of the caller: " + ss.getChannel().getId());

			CallController cc = callSupplier.get();
			cc.init(ss, ari, this);
			try {
				cc.run();
			} catch (Throwable t) {
				logger.severe("Error running the voice application: " + ErrorStream.fromThrowable(t));
				cc.hangup();
			}
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
		logger.warning(e.getMessage());
	}

	/**
	 * The method handles adding a future event from a specific class (event) to the
	 * future event list
	 * 
	 * @param class1
	 *            class of the finished event (example: PlaybackFinished)
	 * @param func
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

	/**
	 * get the name of the application
	 * @return
	 */
	public String getAppName() {
		return appName;
	}
	
	public Exception getLastException() {
		return lastException;
	}

	/**
	 * ignore Stasis start from this channel
	 * 
	 * @param id
	 *            channel id to ignore
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
		}

	}
	
	/**
	 * Get the url that we are connected to
	 * @return
	 */
	public String getConnetion () {
		return ari.getUrl();
	}
	
	/**
	 * return account code of the channel (information about the channel)
	 * @return
	 */
	public String getAccountCode () {
		return callChannel.getAccountcode();
	}
	
	
	/**
	 * get the caller (whom is calling)
	 * @return
	 */
	public String getCallerIdNumber () {
		
		CallerID caller = callChannel.getCaller();
		return caller.getNumber();
	}
	

}

package io.cloudonix.arity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.ChannelStateChange;
import ch.loway.oss.ari4java.generated.ChannelVarset;
import ch.loway.oss.ari4java.generated.Message;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.generated.Variable;

/**
 * View of the current call state.
 * 
 * This class is responsible for receiving notifications from ARI regarding the state of the call
 * and invoking callback listeners for various state change and other events.
 * 
 * @author naamag
 * @author odeda
 */
public class CallState {

	public static enum States {
		Down("Down"),
		Rsrvd("Rsrvd"),
		OffHook("OffHook"),
		Dialing("Dialing"),
		Ring("Ring"),
		Ringing("Ringing"),
		Up("Up"),
		Busy("Busy"),
		DialingOffhook("Dialing Offhook"),
		PreRing("Pre-ring"),
		Hangup(""), // not on official state, just for ease of use
		Unknown("Unknown");
		
		private String stateName;

		States(String stateName) {
			this.stateName = stateName;
		}
		
		public static States find(String state) {
			return Arrays.stream(values()).filter(s -> s.stateName.equalsIgnoreCase(state)).findFirst().orElse(Unknown);
		}
	}
	
	private Logger log;

	private ARI ari;
	private String channelId;
	private ARIty arity;
	private Channel channel;
	private String channelTechnology;
	private States lastState = States.Unknown;
	private boolean isActive = true;
	private boolean wasAnswered = false;
	
	private Map<String, Object> metadata = new ConcurrentHashMap<>();
	private Map<String, String> variables = new ConcurrentHashMap<>();
	private ConcurrentHashMap<States, Queue<Runnable>> stateListeners = new ConcurrentHashMap<>();
	private ConcurrentLinkedQueue<EventHandler<?>> eventListeners = new ConcurrentLinkedQueue<>();

	public CallState(StasisStart callStasisStart, ARIty arity) {
		this(callStasisStart.getChannel(), arity);
	}

	public CallState(Channel chan, ARIty arity) {
		this.ari = arity.getAri();
		this.arity = arity;
		this.channel = chan;
		this.channelId = channel.getId();
		log = Logger.getLogger("CallState[" + channelId + "]");
		this.channelTechnology = channel.getName().split("/")[0];
		lastState = States.find(channel.getState());
		wasAnswered = lastState == States.Up;
		registerEventHandler(ChannelVarset.class, varset -> {
			log.info("Variable set: " + varset.getVariable() + " => " + varset.getValue());
			variables.put(varset.getVariable(), varset.getValue());
		});
		registerEventHandler(ChannelStateChange.class, stateChange -> {
			lastState = States.find(stateChange.getChannel().getState());
			wasAnswered |= lastState == States.Up;
			fireStateChangeListeners();
		});
		registerEventHandler(ChannelHangupRequest.class, hangup -> {
			isActive = false;
			lastState = States.Hangup;
			fireStateChangeListeners();
			// need also to unregister from channel events
			eventListeners.forEach(EventHandler::unregister);
		});
	}

	public ARI getAri() {
		return ari;
	}

	public String getChannelId() {
		return channelId;
	}

	public ARIty getArity() {
		return arity;
	}

	public Channel getChannel() {
		return channel;
	}
	
	public States getStatus() {
		return lastState;
	}
	
	public Map<String, Object> getMetaData() {
		return metadata;
	}

	/**
	 * Store custom meta-data in the transferable call state
	 * @param key   name of the data field to store
	 * @param value Data to store
	 */
	public void put(String key, Object value) {
		if (Objects.isNull(value)) {
			metadata.remove(key); // we aren't allowed to put null values in concurrenthashmap.
			return;
		}
		metadata.put(key, value);
	}

	/**
	 * Load custom meta-data from the transferable call state
	 * 
	 * The data will be cast to the expected data type, so make sure you always store and load the same type
	 * for the same field name
	 * 
	 * @param key name of the data field to load
	 * @return the value stored, casted to the expected type
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(String key) {
		return (T) metadata.get(key);
	}
	
	/**
	 * Check if specific custom meta-data field was stored in the transferable call state
	 * @param key name of the data field to check
	 * @return Whether the field has been previously stored in the call state, even if its value was stored as <tt>null</tt>
	 */
	public boolean contains(String key) {
		return metadata.containsKey(key);
	}
	
	/**
	 * Retrieve an Asterisk channel variable that was set on the current channel
	 * @param name variable name to read
	 * @return variable value
	 */
	public String getVariable(String name) {
		return variables.get(name);
	}
	
	class GetChannelVar extends Operation {
		private String name;
		private String value;

		public GetChannelVar(String channelId, ARIty arity, String varName) {
			super(channelId, arity);
			name = varName;
		}

		@Override
		public CompletableFuture<GetChannelVar> run() {
			return this.<Variable>retryOperation(cb -> channels().getChannelVar(getChannelId(), name, cb))
					.handle((var,e) -> {
						if (Objects.nonNull(e))
							log.info("getVar: " + e);
						else
							value = var.getValue();
						return this;
					});
		}
		
		public String getValue() {
			return value;
		}
		
		@Override
		protected Exception tryIdentifyError(Throwable ariError) {
			return new Exception("Error reading variable " + name + ": " + ariError + ", possibly unset?");
		}
	}

	/**
	 * Retrieve an Asterisk channel variable that was set on the current channel, using
	 * the local variable cache, or trying to retrieve it from ARI if the value is not cached.
	 * @param name variable name to read
	 * @return a promise for a variable value. The promise may resolve to <code>null</code> if the variable
	 * is not set.
	 */
	public CompletableFuture<String> readVariable(String name) {
		if (variables.containsKey(name))
			return CompletableFuture.completedFuture(variables.get(name));
		return new GetChannelVar(channelId, arity, name).run()
			.thenApply(GetChannelVar::getValue)
			.thenApply(val -> { // cache the variable value locally for next time
				if (Objects.nonNull(val))
					variables.put(name, val); // the map can't store nulls
				return val;
			});
	}
	
	class SetChannelVar extends Operation {

		private String name;
		private String value;

		public SetChannelVar(String channelId, ARIty arity, String varName, String varValue) {
			super(channelId, arity);
			name = varName;
			value = varValue;
		}

		@Override
		public CompletableFuture<SetChannelVar> run() {
			return this.<Void>retryOperation(cb -> channels().setChannelVar(getChannelId(), name, value, cb))
					// don't care about errors here - we either managed to set it or the channel doesn't exist anymore
					.handle((v,t) -> this);
		}
		
		@Override
		protected Exception tryIdentifyError(Throwable ariError) {
			return new Exception("Error reading variable " + name + ": " + ariError + ", possibly unset?");
		}
	}
	
	/**
	 * Update an Asterisk channel variable.
	 * @param name variable name to set
	 * @param value variable value to set
	 * @return a promise that will resolve with the variable was set
	 */
	public CompletableFuture<Void> setVariable(String name, String value) {
		if (Objects.nonNull(value)) // the map can't store nulls
			variables.put(name, value);
		return new SetChannelVar(channelId, arity, name, value).run().thenAccept(v -> {});
	}
	
	/**
	 * The Asterisk channel technology for the current channel. ex: SIP, PJSIP
	 * @return
	 */
	public String getChannelTechnology() {
		return channelTechnology;
	}

	/**
	 * Check if the call is still active (had not been disconnected)
	 * @return whether the call is still active
	 */
	public boolean isActive() {
		return isActive;
	}
	
	/**
	 * Check if the call has been answered already.
	 * @return whether the call has been answered.
	 */
	public boolean wasAnswered() {
		return wasAnswered;
	}
	
	/**
	 * Retrieve a promise that will be fulfilled when the call is disconnected
	 * @return
	 */
	public CompletableFuture<Void> waitForHangup() {
		CompletableFuture<Void>future = new CompletableFuture<Void>();
		registerStateHandler(States.Hangup, ()->future.complete(null));
		return future;
	}
	
	/**
	 * Register for getting a callback when the specified state had been reached
	 * @param state The state to listen for
	 * @param handler the handler to run when the state has changed to the specified state
	 */
	public void registerStateHandler(States state, Runnable handler) {
		getStateListeners(state).add(handler);
	}
	
	public <T extends Message> void registerEventHandler(Class<T> type, Consumer<T> eventHandler) {
		eventListeners.add(arity.addEventHandler(type, channelId, (ev, se) -> {
			try {
				eventHandler.accept(ev);
			} catch (Throwable t) {
				log.warning("Error encountered running " + type + " listener " + eventHandler + ": " + t + "\n" + 
						getStackTraceReport(t));
			}
		}));
	}

	/**
	 * Get a list of callback listeners that should be notified when the specific state has been reached,
	 * for read or for write
	 * @param state State for which state listeners are to be retrieved
	 * @return collection of state listeners that is concurrently modifiable
	 */
	private Queue<Runnable> getStateListeners(States state) {
		return stateListeners.computeIfAbsent(state, s -> new ConcurrentLinkedQueue<>());
	}

	/**
	 * Execute state change listeners, with logging of failures
	 */
	private void fireStateChangeListeners() {
		getStateListeners(lastState).forEach(run -> {
			try {
				run.run();
			} catch (Throwable t) {
				log.warning("Error encountered running " + lastState + " listener " + run + ": " + t + "\n" + 
						getStackTraceReport(t));
			}
		});
	}
	
	private String getStackTraceReport(Throwable t) {
		StringWriter stackTrace = new StringWriter(); 
		t.printStackTrace(new PrintWriter(stackTrace));
		return stackTrace.toString();
	}

	@Override
	public String toString() {
		return channelId + "[" + lastState + "]" + variables + "," + metadata;
	}
}

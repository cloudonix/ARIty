package io.cloudonix.arity;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.models.ChannelStateChange;
import ch.loway.oss.ari4java.generated.models.ChannelVarset;
import ch.loway.oss.ari4java.generated.models.Message;
import ch.loway.oss.ari4java.generated.models.StasisEnd;
import ch.loway.oss.ari4java.generated.models.StasisStart;
import ch.loway.oss.ari4java.generated.models.Variable;
import io.cloudonix.arity.errors.ChannelNotFoundException;
import io.cloudonix.arity.helpers.Futures;

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
		Down("Down", true),
		Rsrvd("Rsrvd", false),
		OffHook("OffHook", false),
		Dialing("Dialing", false),
		Ring("Ring", false),
		Ringing("Ringing", false),
		Up("Up", false),
		Busy("Busy", true),
		DialingOffhook("Dialing Offhook", false),
		PreRing("Pre-ring", false),
		Hangup("", true), // not on official state, just for ease of use
		Unknown("Unknown", false);

		private String stateName;
		private boolean terminal;

		States(String stateName, boolean isTerminal) {
			this.stateName = stateName;
			this.terminal = isTerminal;
		}

		public static States find(String state) {
			return Arrays.stream(values()).filter(s -> s.stateName.equalsIgnoreCase(state)).findFirst().orElse(Unknown);
		}
		
		public boolean isTerminal() {
			return terminal;
		}
	}

	private static Logger log = LoggerFactory.getLogger(CallState.class);
	private Marker logmarker;

	private String channelId;
	private ARIty arity;
	private Channel channel;
	private String channelTechnology;
	private States lastState = States.Unknown;
	private volatile boolean isActive = true;
	private volatile boolean wasAnswered = false;

	private Map<String, Object> metadata = new ConcurrentHashMap<>();
	private Map<String, String> variables = new ConcurrentHashMap<>();
	private ConcurrentHashMap<States, Queue<Runnable>> stateListeners = new ConcurrentHashMap<>();
	private ConcurrentLinkedQueue<EventHandler<?>> eventListeners = new ConcurrentLinkedQueue<>();

	public CallState(StasisStart callStasisStart, ARIty arity) {
		this(callStasisStart.getChannel(), arity);
	}

	public CallState(Channel chan, ARIty arity) {
		this.arity = arity;
		this.channel = chan;
		this.channelId = channel.getId();
		logmarker = MarkerFactory.getDetachedMarker(channelId);
		this.channelTechnology = channel.getName().split("/")[0];
		lastState = States.find(channel.getState());
		wasAnswered = lastState == States.Up;
		registerEventHandler(ChannelVarset.class, varset -> {
			log.info(logmarker, "Variable set: " + varset.getVariable() + " => " + varset.getValue());
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
		});
		registerEventHandler(StasisEnd.class, end -> {
			log.info(logmarker, "Stasis application {} ended", end.getChannel().getId());
			isActive = false;
			if (!lastState.isTerminal()) { // simulate hangup, if needed, on stasis end
				lastState = States.Hangup;
				fireStateChangeListeners();
			}
			eventListeners.forEach(EventHandler::unregister);
		});
	}

	/* Useless c'tor, used just so we can fake call controllers not connected to actual ARI service, for testing other things */
	CallState() {}

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

	/**
	 * Pretend a variable with the specified name and value has been read from ARI
	 * This is useful for unit tests as well as to preload variables that have been transported out of band,
	 * for example when reading all SIP headers through a faster transport.
	 * @param name variable name to pre-cache
	 * @param value variable value to pre-cache
	 */
	public void cacheVariable(String name, String value) {
		variables.put(name, value);
	}

	/**
	 * Retrieve all variables that have been already loaded from ARI (or otherwise)
	 * Please note that SIP headers will be included in the list, such that their names look like
	 * they have been read using the Get Variable ARI command, with the format {@code SIP_HEADER(name)}.
	 * @return a set of variable entries.
	 */
	public Set<Entry<String, String>> allVariables() {
		return Collections.unmodifiableSet(variables.entrySet());
	}
	
	@SuppressWarnings("serial")
	private class VariableNotFound extends Exception {}

	class GetChannelVar extends Operation {
		private String name;
		private String value;

		public GetChannelVar(String channelId, ARIty arity, String varName) {
			super(channelId, arity);
			name = varName;
		}

		@Override
		public CompletableFuture<GetChannelVar> run() {
			return Operation.<Variable>retry(cb -> channels().getChannelVar(getChannelId(), name).execute(cb), this::mapExceptions)
					.handle((var,e) -> {
						if (e instanceof VariableNotFound)
							log.info(logmarker, "readVariable({}): not found", name);
						else if (e != null)
							log.info(logmarker, "readVariable({}): unexpected error", name, e);
						else
							value = var.getValue();
						return this;
					});
		}

		public String getValue() {
			return value;
		}

		private Exception mapExceptions(Throwable ariError) {
			switch (ariError.getMessage()) {
			case "Unable to read provided function": // Asterisk  returns "unable" when the function exists
					// but reports an error about the arguments, e.g. calling SIP_HEADER() for a non-set header
					return new VariableNotFound();
			case "Provided channel was not found": return new ChannelNotFoundException(ariError);
			case "Provided variable was not found": return new VariableNotFound();
			default:
			return new Exception("Error reading variable " + name + ": " + ariError + ", possibly unset?");
			}
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
		if (!isActive)
			return CompletableFuture.completedFuture(null);
		return new GetChannelVar(channelId, arity, name).run()
			.thenApply(GetChannelVar::getValue)
			.thenApply(val -> { // cache the variable value locally for next time
				if (val != null)
					variables.put(name, val); // the map can't store nulls
				log.debug("Read channel variable {}: {}", name, val);
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
			return this.<Void>retryOperation(cb -> channels().setChannelVar(getChannelId(), name).setValue(value).execute(cb))
					// don't care about errors here - we either managed to set it or the channel doesn't exist anymore
					.handle((v,t) -> this);
		}

		@Override
		protected Exception tryIdentifyError(Throwable ariError) {
			switch (ariError.getMessage()) {
			case "Provided channel was not found": return new ChannelNotFoundException(ariError);
			default:
			return new Exception("Error reading variable " + name + ": " + ariError + ", possibly unset?");
			}
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
		if (!isActive)
			return CompletableFuture.completedFuture(null);
		return new SetChannelVar(channelId, arity, name, value).run().thenAccept(v -> {});
	}
	
	/**
	 * Update multiple asterisk channel variables at once
	 * @param variables a set of variables
	 * @return a promise that will resolve when all variables have been set
	 */
	public CompletableFuture<Void> setVariables(Map<String,String> variables) {
		return variables.entrySet().stream().map(e -> setVariable(e.getKey(), e.getValue()))
				.collect(Futures.resolvingCollector()).thenAccept(v -> {});
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
		if (getStatus() == States.Hangup)
			future.complete(null);
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
				log.warn(logmarker, "Error encountered running " + type + " listener " + eventHandler, t);
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
				log.warn(logmarker, "Error encountered running " + lastState + " listener " + run, t);
			}
		});
	}

	@Override
	public String toString() {
		return channelId + "[" + lastState + "]" + variables + "," + metadata;
	}
}

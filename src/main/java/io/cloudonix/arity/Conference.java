package io.cloudonix.arity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.models.ChannelEnteredBridge;
import ch.loway.oss.ari4java.generated.models.ChannelLeftBridge;
import ch.loway.oss.ari4java.generated.models.ChannelTalkingFinished;
import ch.loway.oss.ari4java.generated.models.ChannelTalkingStarted;
import ch.loway.oss.ari4java.generated.models.Playback;
import io.cloudonix.arity.errors.ConferenceException;
import io.cloudonix.arity.errors.bridge.BridgeNotFoundException;
import io.cloudonix.arity.helpers.Futures;
import io.cloudonix.arity.models.AsteriskBridge;
import io.cloudonix.arity.models.AsteriskChannel;
import io.cloudonix.arity.models.AsteriskChannel.Mute;
import io.cloudonix.arity.models.AsteriskRecording;

/**
 * The class handles and saves all needed information for a conference call
 *
 * @author naamag
 * @author odeda
 */
public class Conference {
	private CallController callController;
	private final static Logger logger = LoggerFactory.getLogger(Conference.class);
	private Runnable handleChannelLeftConference = () -> {};
	private String recordName = null;
	private AtomicReference<AsteriskRecording> conferenceRecord = new AtomicReference<>(null);
	private CompletableFuture<AsteriskBridge> bridge;
	private String musicOnHoldClassName = "default";
	private ARIty arity;
	private String conferenceName;
	private boolean mohStarted = false;
	private boolean mohStopped = false;
	private Runnable talkingStatedHandler = ()->{};
	private Runnable talkingFinishedEvent = ()->{};
	// start conference states:
	private boolean beepOnEnter = true;
	private boolean mute = false;
	private Mute muteChannelOnStart = Mute.NO;
	private boolean absorbDTMF = false;
	private boolean prompts;
	private String role;
	private EventHandler<ChannelEnteredBridge> memberAddedHandler;
	private EventHandler<ChannelLeftBridge> memberRemovedHandler;

	/**
	 * Create a new conference bridge for this call controller
	 * @param callController call controller to create the conference bridge for
	 */
	public Conference(CallController callController) {
		this(callController, null);
	}

	/**
	 * Create a conference bridge to wrap an existing bridge
	 * @param callController call controller to create the conference bridge for
	 * @param bridgeId existing bridge ID to use for the conference
	 */
	public Conference(CallController callController, String bridgeId) {
		this.arity = callController.getARIty();
		this.callController = callController;
		this.bridge = bridgeId != null ? arity.bridges().get(bridgeId) : new CompletableFuture<>();
		callController.talkDetection(1500, 750);
		arity.addEventHandler(ChannelTalkingStarted.class, callController.getChannelId(),this::memberTalkingStartedEvent);
		arity.addEventHandler(ChannelTalkingFinished.class, callController.getChannelId(),this::memberTalkingFinishedEvent);
	}

	public void memberTalkingStartedEvent(ChannelTalkingStarted talkingStarted, EventHandler<ChannelTalkingStarted>se) {
		this.talkingStatedHandler.run();
	}

	public void memberTalkingFinishedEvent(ChannelTalkingFinished talkingStarted, EventHandler<ChannelTalkingFinished>se) {
		this.talkingFinishedEvent .run();
	}

	public Conference registerMemberStartedTalkingHandler(Runnable talkingStartedH) {
		this.talkingStatedHandler = talkingStartedH;
		return this;
	}

	public Conference registerMemberFinishedTalkingHandler(Runnable talkingFinishedH) {
		this.talkingFinishedEvent = talkingFinishedH;
		return this;
	}
	
	/**
	 * Sets whether a beep will be played to the channel before entering the conference.
	 * @param beepOnEnter whether the caller will hear a beep when entering the conference
	 * @return itself for fluent calls
	 */
	public Conference beepOnEnter(boolean beepOnEnter) {
		this.beepOnEnter = beepOnEnter;
		return this;
	}

	/**
	 * Sets whether the channel should be muted in the conference.
	 * The channel's input audio will not be played out to the conference at all. It is not
	 * possible to unmute a channel muted that way - the channel must be disconnected from
	 * the conference and be reconnected in order to achieve that.
	 * @param mute whether to mute the channel in the conference
	 * @return itself for fluent calls
	 */
	public Conference muteInConference(boolean mute) {
		this.mute = mute;
		return this;
	}
	
	/**
	 * Sets whether the channel should be muted before entering the conference.
	 * This behavior is different than {@link #muteInConference(boolean)} in that the channel is muted
	 * using the channel API and it can later be unmuted using the channel API.
	 * @param muteDirection channel mute direction to be applied to the channel before it enters the conference.
	 * @return itself for fluent calls
	 */
	public Conference muteChannelOnStart(AsteriskChannel.Mute muteDirection) {
		this.muteChannelOnStart = muteDirection;
		return this;
	}
	
	/**
	 * Sets whether DTMF coming from this channel, should be absorbed - preventing it from passing through to the
	 * conference.
	 * @param absorbDTMF should DTMF from the channel should be absorbed
	 * @return itself for fluent calls
	 */
	public Conference absorbDTMF(boolean absorbDTMF) {
		this.absorbDTMF = absorbDTMF;
		return this;
	}
	
	/**
	 * Sets whether the channel should be announced to the conference when entering and leaving the conference
	 * @param prompts whether channel entering/leaving prompts should be played to the conference
	 * @return itself for fluent calls
	 */
	public Conference announce(boolean prompts) {
		this.prompts = prompts;
		return this;
	}
	
	/**
	 * Sets the channel's role in the bridge
	 * @param role role name
	 * @return itself for fluent calls
	 */
	public Conference role(String role) {
		this.role = role;
		return this;
	}
	
	/**
	 * Close conference
	 * @return a promise that will resolve with the conference bridge has been shutdown
	 */
	public CompletableFuture<Void> closeConference() {
		logger.info("Closing conference");
		return bridge.thenCompose(b -> b.destroy())
				.thenRun(() -> {
					memberAddedHandler.unregister();
					memberRemovedHandler.unregister();
				});
	}

	/**
	 * Add a channel to the conference
	 * @param beep should a "beep" sound play when the channel joins the conference
	 * @param mute should the new channel be muted
	 * @return A promise that will resolve when the channel has been added and announced
	 * @deprecated Use configure the connection using the connection modifiers than {@link #addChannel()} instead.
	 */
	public CompletableFuture<Conference> addChannelToConf(boolean beep, Mute mute) {
		return beepOnEnter(beep).muteChannelOnStart(mute).announce(true).addChannel();
	}
	
	/**
	 * Add a channel to the conference
	 * @param beep should a "beep" sound play when the channel joins the conference
	 * @param mute should the new channel be muted
	 * @return A promise that will resolve when the channel has been added and announced
	 * @deprecated Use configure the connection using the connection modifiers than {@link #addChannel()} instead.
	 */
	public CompletableFuture<Conference> addChannelToConf(boolean beep, Mute mute, boolean announce) {
		return beepOnEnter(beep).muteChannelOnStart(mute).announce(announce).addChannel();
	}
	
	/**
	 * Add the channel to the conference
	 * Use {@link #beepOnEnter(boolean)}, {@link #muteInConference(boolean)}, {@link #muteChannelOnStart(Mute)},
	 * {@link #absorbDTMF(boolean)}, and {@link #announce(boolean)} to configure the connection to
	 * the bridge.
	 * @return A promise that will resolve when the channel has been added and announced
	 */
	public CompletableFuture<Conference> addChannel() {
		CompletableFuture<Void> preConnect = CompletableFuture.completedFuture(null);
		if (callController.getCallState().wasAnswered()) {
			logger.info("Channel with id: " + callController.getChannelId() + " was already answered");
		} else {
			logger.debug("Need to answer the channel with id: " + callController.getChannelId());
			preConnect = callController.answer().run().thenAccept(__ -> {});
		}
		arity.listenForOneTimeEvent(ChannelLeftBridge.class, callController.getChannelId(),
				e -> channelLeftConference(e)
				.exceptionally(Futures.on(BridgeNotFoundException.class, ex -> null)) // this can happen when conferences are closed quickly 
				.whenComplete((v,t) -> {
					if (t != null)
						logger.error("Error handling the 'channel left bridge' event:",t);
				}));
		return preConnect.thenCompose(__ -> muteChannelIfNeeded()).thenCompose(answerRes -> bridge)
				.thenCompose(b -> b.addChannel(callController.getChannelId(), true, req -> {
					if (role != null) req.setRole(role);
					if (mute) req.setMute(true);
					if (absorbDTMF) req.setAbsorbDTMF(true);
				}))
				.thenRun(() -> logger.debug("Channel was added to the bridge"))
				.thenCompose(__ -> beepOnEnter ? playMedia("beep") : CompletableFuture.completedFuture(null))
				.thenCompose(muteRes -> annouceUser(prompts ? UserAnnounce.joined : UserAnnounce.quiet))
				.exceptionally(Futures.on(Exception.class, t -> {
					logger.info("Unable to add channel to conference: " + t);
					throw new ConferenceException(t);
				}))
				.thenApply(v -> this);
	}
	
	private CompletableFuture<Void> muteChannelIfNeeded() {
		if (muteChannelOnStart == Mute.NO)
			return CompletableFuture.completedFuture(null);
		return callController.mute(muteChannelOnStart).run()
				// ignore errors in unmuting channels, these are likely "channel not found"
				.exceptionally(__ -> null)
				.thenAccept(__ -> {});
	}
	
	private CompletableFuture<Void> unmuteChannelIfNeeded() {
		if (muteChannelOnStart == Mute.NO)
			return CompletableFuture.completedFuture(null);
		return callController.mute(Mute.NO).run()
				// ignore errors in unmuting channels, these are likely "channel not found"
				.exceptionally(__ -> null)
				.thenAccept(__ -> {});
	}
	
	/**
	 * Start recording the conference
	 * @return a promise that will be resolved when the recording starts
	 */
	public CompletableFuture<AsteriskRecording> recordConference() {
		logger.info("Starting recording conference {}", conferenceName);
		if (Objects.isNull(recordName))
			recordName = UUID.randomUUID().toString();
		return bridge.thenCompose(b -> b.record(a -> a.withName(recordName).withIfExists("overwrite").withPlayBeep(false)))
				.thenApply(rec -> {
					conferenceRecord.set(rec);
					logger.info("Started recording {} [{}]", conferenceName, recordName);
					return rec;
				});
	}
	
	/**
	 * Stop the recording of this channel in the bridge, after which the recording is stored and can be read
	 * @return promise that will resolve when the recording ends, or immediately if there was no live recording
	 */
	public CompletableFuture<Void> stopRecording() {
		if (conferenceRecord.get() == null)
			return CompletableFuture.completedFuture(null);
		return conferenceRecord.get().stop().thenAccept(v -> {});
	}

	/**
	 * Register callback that will be invoked when the channel has left the conference.
	 * @param handler handler to be called when the channel had disconnected from the conference
	 * @return itself for fluent calls
	 */
	public Conference registerChannelLeftConferenceHandler(Runnable handler) {
		handleChannelLeftConference = handler;
		return this;
	}

	/**
	 * handle when a channel left conference bridge
	 *
	 * @param channelLeftBridge channelLeftBridge event instance
	 * @param joinLeavePrompts 
	 */
	private CompletableFuture<Void> channelLeftConference(ChannelLeftBridge channelLeftBridge) {
		handleChannelLeftConference.run();
		logger.info("Channel " + channelLeftBridge.getChannel().getId() + " left conference: " + conferenceName);
		return CompletableFuture.allOf(unmuteChannelIfNeeded(),
				annouceUser(prompts ? UserAnnounce.left : UserAnnounce.quiet))
				.thenCompose(__ -> bridge)
				.thenCompose(b -> b.getChannelCount()).thenAccept(numberOfChannelsInConf -> {
					// Let's not do that - there's no API to control whether moh sould play, so by default lets not be noisy
					/*
					if (numberOfChannelsInConf == 1) {
						logger.info("Only one channel left in bridge, play music on hold");
						bridge.thenCompose(b -> b.startMusicOnHold(musicOnHoldClassName));
					}
					*/
					if (numberOfChannelsInConf == 0) {
						closeConference().thenAccept(
								v2 -> logger.info("Nobody in the conference, closed the conference" + conferenceName))
								.exceptionally(t -> {
									logger.warn("Conference bridge was already destroyed");
									return null;
								});
					}
				});
	}
	
	/**
	 * Register callback that will be invoked when any of other conference member has entered the conference.
	 * @param handler handler to be called when a member has joined the conference
	 * @return itself for fluent calls
	 */
	public Conference registerMemberAddedHandler(Runnable handler) {
		bridge.thenAccept(b -> {
			var bridgeId = b.getId();
			memberAddedHandler = arity.addGeneralEventHandler(ChannelEnteredBridge.class, (e, eh) -> {
				if (!Objects.equals(bridgeId, e.getBridge().getId()) ||
						Objects.equals(callController.getChannelId(), e.getChannel().getId()))
					return;
				handler.run();
			});
		});
		return this;
	}

	/**
	 * Register callback that will be invoked when any of other conference member has entered the conference.
	 * @param handler handler to be called when a member has joined the conference
	 * @return itself for fluent calls
	 */
	public Conference registerMemberRemovedHandler(Runnable handler) {
		bridge.thenAccept(b -> {
			var bridgeId = b.getId();
			memberRemovedHandler = arity.addGeneralEventHandler(ChannelLeftBridge.class, (e, eh) -> {
				if (!Objects.equals(bridgeId, e.getBridge().getId()) ||
						Objects.equals(callController.getChannelId(), e.getChannel().getId()))
					return;
				handler.run();
			});
		});
		return this;
	}
	
	public enum UserAnnounce { quiet, joined, left }

	/**
	 * Announce new channel joined/left a conference
	 *
	 * @param status 'joined' or 'left' conference
	 */
	public CompletableFuture<Void> annouceUser(UserAnnounce status) {
		switch (status) {
		case joined: return playMedia("confbridge-has-joined").thenAccept(v -> {});
		case left: return playMedia("conf-hasleft").thenAccept(v -> {});
		case quiet:
		default:
			return CompletableFuture.completedFuture(null);
		}
	}

	/**
	 * get number of channels in conference
	 *
	 * @return
	 */
	public CompletableFuture<Integer> getCount() {
		return bridge.thenCompose(b -> b.getChannelCount());
	}

	/**
	 * get list of channels connected to the conference bridge
	 *
	 * @return
	 */
	public CompletableFuture<List<String>> getChannelsInConf() {
		return bridge.thenCompose(b -> b.getChannels());
	}

	/**
	 * get recording name of the conference
	 *
	 * @return
	 */
	public String getRecordName() {
		return recordName;
	}

	/**
	 * get the recording of the conference
	 *
	 * @return
	 */
	public AsteriskRecording getConferenceRecord() {
		return conferenceRecord.get();
	}

	/**
	 * get music on hold file name of the conference
	 *
	 * @return
	 */
	public String getMusicOnHoldFileName() {
		return musicOnHoldClassName;
	}

	/**
	 * set music on hold class for conference, defining the needed configuration for
	 * playing music on hold
	 *
	 * @return
	 */
	public void setMusicOnHoldClassName(String musicOnHoldClassName) {
		this.musicOnHoldClassName = musicOnHoldClassName;
	}

	/**
	 * Check if there is a bridge for the conference
	 * @return true if there is a bridge, false otherwise
	 */
	public CompletableFuture<Boolean> doesBridgeExist() {
		return bridge.thenCompose(AsteriskBridge::isActive).thenApply(e -> {
			if (conferenceName == null)
				conferenceName = bridge.getNow(null).getName();
			return e;
		});
	}

	/**
	 * Create a bridge for the conference without selecting the bridge id
	 * @param conferenceName name of the conference
	 * @return the conference bridge
	 */
	public CompletableFuture<AsteriskBridge> createConferenceBridge(String conferenceName) {
		this.conferenceName = conferenceName;
		return (bridge = arity.bridges().create(conferenceName)).thenApply(bridgeRes -> {
			logger.info("Created a conference bridge");
			return bridgeRes;
		});
	}

	/**
	 * remove the call controller from this conference
	 *
	 * @param channelId the id of the channel we want to remove
	 * @return a promise that will complete when the call controller has been removed from the conference bridge
	 */
	public CompletableFuture<Void> removeChannelFromConf() {
		return bridge.thenCompose(b -> b.removeChannel(callController.getChannelId()));
	}

	/**
	 * Retrieve conference name
	 * @return the conference name that was set when creating the conference bridge
	 */
	public String getConferenceName() {
		return conferenceName;
	}

	/**
	 * Play media to the bridge
	 * @param mediaToPlay name of the media to play
	 * @return a promise that will complete when the media has finished playing in the conference bridge, providing
	 * the ARI playback instance
	 */
	public CompletableFuture<Playback> playMedia(String mediaToPlay) {
		return bridge.thenCompose(b -> b.playMedia(mediaToPlay));
	}

	/**
	 * Start playing music on hold to conference bridge
	 * @return a promise that will complete when the music on hold has started
	 */
	public CompletableFuture<Void> startMusicOnHold() {
		return bridge.thenCompose(b -> b.startMusicOnHold(musicOnHoldClassName))
				.thenApply(v->{
					logger.debug("Started playing music on hold to conference bridge");
					mohStarted = true;
					return v;
				}).exceptionally(t->{
					if(t.getMessage().contains("Bridge not in Stasis application")) {
						logger.debug("Music on hold already started, can't start it again");
					}
					mohStarted = false;
					return null;
				});
	}

	/**
	 * Stop playing to music on hold to conference bridge
	 * @return a promise that will complete when the music on hold has stopped playing
	 */
	public CompletableFuture<Void> stopMusicOnHold() {
		return bridge.thenCompose(b -> b.stopMusicOnHold())
				.thenApply(v->{
					logger.debug("Stoped playing music on hold to conference bridge");
					mohStopped = true;
					return v;
				}).exceptionally(t->{
					if(t.getMessage().contains("Bridge not in Stasis application")) {
						logger.debug("Music on hold already stoped, can't stop it again");
					}
					mohStopped = false;
					return null;
				});
	}

	public boolean isMohStarted() {
		return mohStarted;
	}

	public void setMohStarted(boolean mohStarted) {
		this.mohStarted = mohStarted;
	}

	public boolean isMohStopped() {
		return mohStopped;
	}

	public void setMohStopped(boolean monStopped) {
		this.mohStopped = monStopped;
	}
}

package io.cloudonix.arity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.loway.oss.ari4java.generated.models.ChannelLeftBridge;
import ch.loway.oss.ari4java.generated.models.ChannelTalkingFinished;
import ch.loway.oss.ari4java.generated.models.ChannelTalkingStarted;
import ch.loway.oss.ari4java.generated.models.Playback;
import io.cloudonix.arity.errors.ConferenceException;
import io.cloudonix.arity.helpers.Futures;
import io.cloudonix.arity.models.AsteriskBridge;
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
	volatile private AsteriskRecording conferenceRecord;
	private CompletableFuture<AsteriskBridge> bridge;
	private String musicOnHoldClassName = "default";
	private ARIty arity;
	private String conferenceName;
	private boolean mohStarted = false;
	private boolean mohStopped = false;
	private Runnable talkingStatedHandler = ()->{};
	private Runnable talkingFinishedEvent = ()->{};

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
	 * Close conference
	 * @return a promise that will resolve with the conference bridge has been shutdown
	 */
	public CompletableFuture<Void> closeConference() {
		logger.info("Closing conference");
		return bridge.thenCompose(b -> b.destroy());
	}

	/**
	 * Add a channel to the conference
	 * @param beep should a "beep" sound play when the channel joins the conference
	 * @param mute should the new channel be muted
	 * @return A promise that will resolve when the channel has been added and announced
	 */
	public CompletableFuture<Conference> addChannelToConf(boolean beep, Mute mute) {
		return addChannelToConf(beep, mute, true);
	}
	
	/**
	 * Add a channel to the conference
	 * @param beep should a "beep" sound play when the channel joins the conference
	 * @param mute should the new channel be muted
	 * @return A promise that will resolve when the channel has been added and announced
	 */
	public CompletableFuture<Conference> addChannelToConf(boolean beep, Mute mute, boolean joinLeavePrompts) {
		CompletableFuture<Answer> answer = new CompletableFuture<Answer>();
		if (callController.getCallState().wasAnswered()) {
			logger.info("Channel with id: " + callController.getChannelId() + " was already answered");
			answer.complete(null);
		} else {
			logger.debug("Need to answer the channel with id: " + callController.getChannelId());
			answer = callController.answer().run();
		}
		return answer.thenCompose(answerRes -> bridge).thenCompose(b -> b.addChannel(callController.getChannelId()))
				.thenCompose(v -> {
					logger.debug("Channel was added to the bridge");
					return beep ? playMedia("beep") : CompletableFuture.completedFuture(null);
				}).thenCompose(beepRes -> {
					arity.listenForOneTimeEvent(ChannelLeftBridge.class, callController.getChannelId(),
							e -> channelLeftConference(e, joinLeavePrompts));
					return callController.mute(mute).run();
				})
				.thenCompose(muteRes -> annouceUser(joinLeavePrompts ? UserAnnounce.joined : UserAnnounce.quiet))
				.exceptionally(Futures.on(Exception.class, t -> {
					logger.info("Unable to add channel to conference: " + t);
					throw new ConferenceException(t);
				}))
				.thenApply(v -> this);
	}

	/**
	 * Start recording the conference
	 * Call {@link #setRecordName(String)} to set the name of the recording, otherwise the name will be
	 * a random UUID.
	 * @return a promise that will be resolved when the recording starts
	 */
	public CompletableFuture<Void> recordConference() {
		logger.info("Starting recording conference " + conferenceName);
		if (Objects.isNull(recordName))
			recordName = UUID.randomUUID().toString();
		return bridge.thenCompose(b -> b.record(a -> a.withName(recordName).withIfExists("overwrite").withPlayBeep(false)))
				.thenAccept(recored -> {
					conferenceRecord = recored;
					logger.info("Started recording");
				});
	}
	
	/**
	 * Stop the recording of this channel in the bridge, after which the recording is stored and can be read
	 * @return promise that will resolve when the recording ends, or immediately if there was no live recording
	 */
	public CompletableFuture<Void> stopRecording() {
		if (Objects.isNull(conferenceRecord))
			return CompletableFuture.completedFuture(null);
		return conferenceRecord.stop().thenAccept(v -> {});
	}

	/**
	 * register handler to execute when channel left conference
	 *
	 * @param handler runnable function that will be running when channel left
	 *                bridge occurs
	 * @return
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
	private void channelLeftConference(ChannelLeftBridge channelLeftBridge, boolean joinLeavePrompts) {
		handleChannelLeftConference.run();
		logger.info("Channel " + channelLeftBridge.getChannel().getId() + " left conference: " + conferenceName);
		annouceUser(joinLeavePrompts ? UserAnnounce.left : UserAnnounce.quiet).thenAccept(pb -> {
			bridge.thenCompose(b -> b.getChannelCount()).thenAccept(numberOfChannelsInConf -> {
				if (numberOfChannelsInConf == 1) {
					logger.info("Only one channel left in bridge, play music on hold");
					bridge.thenCompose(b -> b.startMusicOnHold(musicOnHoldClassName));
				}
				if (numberOfChannelsInConf == 0) {
					closeConference().thenAccept(
							v2 -> logger.info("Nobody in the conference, closed the conference" + conferenceName))
							.exceptionally(t -> {
								logger.warn("Conference bridge was already destroyed");
								return null;
							});
				}
			});
		});
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
	 * set recording name of the conference
	 *
	 * @return
	 */
	public void setRecordName(String recordName) {
		this.recordName = recordName;
	}

	/**
	 * get the recording of the conference
	 *
	 * @return
	 */
	public AsteriskRecording getConferenceRecord() {
		return conferenceRecord;
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

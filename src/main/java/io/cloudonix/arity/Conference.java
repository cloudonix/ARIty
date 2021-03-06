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
import io.cloudonix.lib.Futures;

/**
 * The class handles and saves all needed information for a conference call
 *
 * @author naamag
 *
 */
public class Conference {
	private CallController callController;
	private final static Logger logger = LoggerFactory.getLogger(Conference.class);
	private Runnable handleChannelLeftConference = () -> {};
	private String recordName = null;
	volatile private RecordingData conferenceRecord;
	private Bridge bridge;
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
		this(callController, new Bridge(callController.getARIty()));
	}

	/**
	 * Create a conference bridge to wrap an existing bridge
	 * @param callController call controller to create the conference bridge for
	 * @param bridgeId existing bridge ID to use for the conference
	 */
	public Conference(CallController callController, String bridgeId) {
		this(callController, new Bridge(callController.getARIty(), bridgeId));
	}

	private Conference(CallController callController, Bridge bridge) {
		this.arity = callController.getARIty();
		this.callController = callController;
		this.bridge = bridge;
		callController.setTalkingInChannel("set", "1500,750");
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
		return bridge.destroy();
	}

	/**
	 * Add a channel to the conference
	 * @param beep should a "beep" sound play when the channel joins the conference
	 * @param mute should the new channel be muted
	 * @return A promise that will resolve when the channel has been added and announced
	 */
	public CompletableFuture<Conference> addChannelToConf(boolean beep, boolean mute) {
		return addChannelToConf(beep, mute, true);
	}
	
	/**
	 * Add a channel to the conference
	 * @param beep should a "beep" sound play when the channel joins the conference
	 * @param mute should the new channel be muted
	 * @return A promise that will resolve when the channel has been added and announced
	 */
	public CompletableFuture<Conference> addChannelToConf(boolean beep, boolean mute, boolean joinLeavePrompts) {
		CompletableFuture<Answer> answer = new CompletableFuture<Answer>();
		if (callController.getCallState().wasAnswered()) {
			logger.info("Channel with id: " + callController.getChannelId() + " was already answered");
			answer.complete(null);
		} else {
			logger.debug("Need to answer the channel with id: " + callController.getChannelId());
			answer = callController.answer().run();
		}
		return answer.thenCompose(answerRes -> bridge.addChannel(callController.getChannelId()))
				.thenCompose(v -> {
					logger.debug("Channel was added to the bridge");
					return beep ? playMedia("beep") : CompletableFuture.completedFuture(null);
				}).thenCompose(beepRes -> {
					arity.listenForOneTimeEvent(ChannelLeftBridge.class, callController.getChannelId(),
							e -> channelLeftConference(e, joinLeavePrompts));
					return mute ? callController.mute(callController.getChannelId(), "out").run()
							: CompletableFuture.completedFuture(null);
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
		return bridge.record(recordName, "overwrite", false, null, null, 0, 0).thenAccept(recored -> {
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
		return bridge.stopRecording(conferenceRecord.getRecordingName()).thenAccept(v -> {});
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
			bridge.getChannelCount().thenAccept(numberOfChannelsInConf -> {
				if (numberOfChannelsInConf == 1) {
					logger.info("Only one channel left in bridge, play music on hold");
					bridge.startMusicOnHold(musicOnHoldClassName);
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
	 * get conference name
	 *
	 * @return
	 */
	public String getConfName() {
		return conferenceName;
	}

	/**
	 * get number of channels in conference
	 *
	 * @return
	 */
	public CompletableFuture<Integer> getCount() {
		return bridge.getChannelCount();
	}

	/**
	 * get list of channels connected to the conference bridge
	 *
	 * @return
	 */
	public CompletableFuture<List<String>> getChannelsInConf() {
		return bridge.getChannels();
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
	public RecordingData getConferenceRecord() {
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
	 * check if there is a bridge for the conference
	 *
	 * @return true if there is a bridge, false otherwise
	 */
	public CompletableFuture<Boolean> doesBridgeExist() {
		return bridge.isActive();
	}

	/**
	 * create a bridge for the conference without selecting the bridge id
	 *
	 * @param conferenceName name of the conference
	 *
	 * @return the conference bridge
	 */
	public CompletableFuture<Bridge> createConferenceBridge(String conferenceName) {
		this.conferenceName = conferenceName;
		return bridge.create(conferenceName).thenApply(bridgeRes -> {
			logger.info("Created a conference bridge");
			return bridgeRes;
		});
	}

	/**
	 * remove a channel from this conference
	 *
	 * @param channelId the id of the channel we want to remove
	 * @return
	 */
	public CompletableFuture<Void> removeChannelFromConf(String channelId) {
		return bridge.removeChannel(channelId);
	}

	/**
	 * get conference name
	 *
	 * @return
	 */
	public String getConferenceName() {
		return conferenceName;
	}

	/**
	 * play media to the bridge
	 *
	 * @param mediaToPlay name of the media to play
	 * @return promise to a Playback
	 */
	public CompletableFuture<Playback> playMedia(String mediaToPlay) {
		return bridge.playMedia(mediaToPlay);
	}

	/**
	 * remove channel from conference
	 *
	 * @param channelID id of the channel we want to remove
	 * @return
	 */
	public CompletableFuture<Void> removeChannel(String channelID) {
		return bridge.removeChannel(channelID);
	}

	/**
	 * start playing music on hold to conference bridge
	 * @return
	 */
	public CompletableFuture<Void> startMusicOnHold() {
		return bridge.startMusicOnHold(musicOnHoldClassName)
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
	 * stop playing to music on hold to conference bridge
	 * @return
	 */
	public CompletableFuture<Void> stopMusicOnHold() {
		return bridge.stopMusicOnHold()
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

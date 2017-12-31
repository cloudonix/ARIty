package io.cloudonix.myAriProject;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriFactory;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.Dial;
import ch.loway.oss.ari4java.generated.Message;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;

public class App {

	private final static Logger logger = Logger.getLogger(App.class.getName());

	// map for each verb
	private static ConcurrentMap<String, CompletableFuture<Playback>> playMap = new ConcurrentHashMap<String, CompletableFuture<Playback>>();
	private static ConcurrentMap<String, CompletableFuture<Dial>> dialMap = new ConcurrentHashMap<String, CompletableFuture<Dial>>();

	public static void main(String[] args) {

		AtomicReference<StasisStart> ss = new AtomicReference<StasisStart>(null);

		try {

			ARI ari = AriFactory.nettyHttp("http://127.0.0.1:8088/", "userid", "secret", AriVersion.ARI_2_0_0);

			ari.events().eventWebsocket("stasisAPP", true, new AriCallback<Message>() {

				@Override
				public void onFailure(RestException e) {
					// TODO Auto-generated method stub
					e.printStackTrace();
				}

				@Override
				public void onSuccess(Message result) {

					String as_id = result.getAsterisk_id();
					//logger.info("success! Asterisk id: " + as_id);

					if (result instanceof StasisStart) {
						ss.set((StasisStart) result);
						// StasisStart case
						voiceApp(ari, ss);

					} else if (result instanceof PlaybackFinished) {
						// PlaybackFinished case

						// Playback playback = Objects.requireNonNull(((PlaybackFinished)
						// result).getPlayback(), "error playback");
						Playback playback = ((PlaybackFinished) result).getPlayback();
						
						
						
						CompletableFuture<Playback> pbfFuture = playMap.get(playback.getId());
						logger.info("playback completed");

						// remove from playMap
						playMap.remove(playback.getId());

						pbfFuture.complete(playback);

					}

				}
			});

		} catch (Throwable t) {
			t.printStackTrace();
		}

	}

	private static CompletableFuture<Playback> play(ARI ari, AtomicReference<StasisStart> s) {

		Channel currChannel = s.get().getChannel();
		String channID = currChannel.getId();

		CompletableFuture<Playback> res = new CompletableFuture<Playback>();
		
		String pbID = UUID.randomUUID().toString();
		playMap.put(pbID, res);

		ari.channels().play(channID, "sound:hello-world", currChannel.getLanguage(), 0, 0,pbID,
				new AriCallback<Playback>() {

					@Override
					public void onFailure(RestException e) {
						// TODO Auto-generated method stub
						res.completeExceptionally(e);
						e.printStackTrace();
					}

					@Override
					public void onSuccess(Playback resultM) {

						if (!(resultM instanceof Playback))
							return;

						// get the id from the playback
						String pbID = resultM.getId();
						// add to map with playback id and playback future
						logger.info("playback added");
						
					}

				});
		return res;
	}

	private static CompletableFuture<Void> hangUpCall(ARI ari, AtomicReference<StasisStart> ss) {
		// TODO Auto-generated method stub
		String currChannel = ss.get().getChannel().getId();
		try {
			ari.channels().hangup(currChannel, "normal");
		} catch (RestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return CompletableFuture.completedFuture(null);
	}

	private static CompletableFuture<Void> answer(ARI ari, AtomicReference<StasisStart> ss) {
		// TODO Auto-generated method stub
		String currChannel = ss.get().getChannel().getId();

		try {
			// answer the call
			ari.channels().answer(currChannel);
		} catch (RestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return CompletableFuture.completedFuture(null);

	}

	private static void voiceApp(ARI ari, AtomicReference<StasisStart> ss) {
		// CompletableFuture<Void> cf = CompletableFuture.completedFuture(null);

		// answer the call
		// ari.channels().answer(channID);
		answer(ari, ss).thenCompose(v -> {
			logger.info("call answered");

			// play (complete somehow that the user will call play)

			return play(ari, ss);
		}).thenCompose(pb -> {

			logger.info("finished playback! id: " + pb.getId());

			// hang up the call
			return hangUpCall(ari, ss);
		}).thenAccept(h -> {
			logger.info("hang up call");

		}).exceptionally(t -> {
			logger.severe(t.toString());
			return null;
		});

	}
}

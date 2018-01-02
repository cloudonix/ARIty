package io.cloudonix.myAriProject;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.Iterator;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriFactory;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.Message;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;

public class App {

	private final static Logger logger = Logger.getLogger(App.class.getName());

	//private static ConcurrentMap<String, CompletableFuture<Dial>> dialMap = new ConcurrentHashMap<String, CompletableFuture<Dial>>();
	private static CopyOnWriteArrayList<Map.Entry<Predicate<Message>, Consumer<Message>>> futureEvents = new CopyOnWriteArrayList<Map.Entry<Predicate<Message>, Consumer<Message>>>();

	public static void main(String[] args) {

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

					if (result instanceof StasisStart) {
						// StasisStart case
						voiceApp(ari, (StasisStart) result);

					}

					
					Iterator<Entry<Predicate<Message>, Consumer<Message>>> itr = futureEvents.iterator();
					
					while(itr.hasNext()) {
						Entry<Predicate<Message>, Consumer<Message>> currEntry = itr.next();
						if (currEntry.getKey().test(result)) {
							currEntry.getValue().accept(result);
							//remove from the list of future events
							itr.remove();
							break;
						}
					}
					
					/*for (Map.Entry<Predicate<Message>, Consumer<Message>> entry : futureEvents) {
						if (entry.getKey().test(result)) {
							entry.getValue().accept(result);
							break;
						}
					}*/

				}
			});

		} catch (Throwable t) {
			t.printStackTrace();
		}

	}

	private static CompletableFuture<Playback> play(ARI ari, StasisStart result) {

		Channel currChannel = result.getChannel();
		String channID = currChannel.getId();

		CompletableFuture<Playback> res = new CompletableFuture<Playback>();

		String pbID = UUID.randomUUID().toString();
		logger.info("UUID: "+ pbID);

		// add to map with playback id and playback future

		ari.channels().play(channID, "sound:hello-world", currChannel.getLanguage(), 0, 0, pbID,
 				new AriCallback<Playback>() {

					@Override
					public void onFailure(RestException e) {
						// TODO Auto-generated method stub
						res.completeExceptionally(e);
						// e.printStackTrace();
					}

					@Override
					public void onSuccess(Playback resultM) {

						if (!(resultM instanceof Playback))
							return;

						logger.info("playback started! playback id: " + resultM.getId());

						// add a playback finished future event to the futureEvent list
						Predicate<Message> pred = (Message pb) -> {

							if (!(pb instanceof PlaybackFinished))
								return false;
							PlaybackFinished playb = (PlaybackFinished) pb;
							if (!(playb.getPlayback().getId().equals(pbID)))
								return false;
							logger.info("playbackFinished and same playback id. Id is: "+ pbID);
							return true;
						};

						Consumer<Message> consumer = (pbf) -> {
							logger.info("playback completed");
							res.complete(((PlaybackFinished) pbf).getPlayback());

						};

						futureEvents.add(new SimpleEntry<Predicate<Message>, Consumer<Message>>(pred, consumer));
						logger.info("future event of playback finished was added");


						// waitForPBtoFinished((PlaybackFinished pEnd) -> res.complete(resultM));
						// waitForPBtoFinished(pbID).thenAccept((PlaybackFinished pEnd) ->
						// res.complete(resultM));
					}

				});

		return res;
	}


	private static CompletableFuture<Void> hangUpCall(ARI ari, StasisStart result) {
		// TODO Auto-generated method stub
		String currChannel = result.getChannel().getId();
		try {
			ari.channels().hangup(currChannel, "normal");
		} catch (RestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return CompletableFuture.completedFuture(null);
	}

	private static CompletableFuture<Void> answer(ARI ari, StasisStart result) {
		// TODO Auto-generated method stub
		String currChannel = result.getChannel().getId();

		try {
			// answer the call
			ari.channels().answer(currChannel);
		} catch (RestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.info("call answered");
		return CompletableFuture.completedFuture(null);

	}

	private static CompletableFuture<Playback> play(ARI ari, StasisStart result, int times) {
		// TODO Auto-generated method stub

		if (times == 1) {
			return play(ari, result);
		}

		return play(ari, result, times - 1).thenCompose(x -> play(ari, result));

	}

	private static void voiceApp(ARI ari, StasisStart result) {
		// CompletableFuture<Void> cf = CompletableFuture.completedFuture(null);

		// answer the call
		answer(ari, result).thenCompose(v -> play(ari, result))
		.thenCompose(pb -> {
			logger.info("finished playback! id: " + pb.getId());

			// hang up the call
			return hangUpCall(ari, result);
		}).thenAccept(h -> {
			logger.info("hanged up call");

		}).exceptionally(t -> {
			logger.severe(t.toString());
			return null;
		});

	}
}

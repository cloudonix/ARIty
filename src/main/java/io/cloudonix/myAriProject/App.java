package io.cloudonix.myAriProject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

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
	// list of lists (each list is a verb)
	private static Map<String, Map<String, CompletableFuture<Object>>> verbs = new HashMap<>();
//	private static Map<String, CompletableFuture<Playback>> play =  Collections.synchronizedMap(new HashMap<String, CompletableFuture<Playback>>());

	public static void main(String[] args) {
		CreateList();
		
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
					logger.info("success! Asterisk id: " + as_id);

					if (result instanceof StasisStart) {
						// StasisStart case

						// StasisStart event = (StasisStart) ss;
						// latchStasis.await();
						/*ss.set((StasisStart) result);
						
						logger.info("the channel id is:" + channID);*/
						// answer the call

						voiceApp(ari, ss);

					} else if (result instanceof PlaybackFinished) {
						// PlaybackFinished case

						CompletableFuture<Object> pbfFuture = getFuture("play",
								((PlaybackFinished) result).getPlayback().getId());
						logger.info("playback completed");
						pbfFuture.complete(((PlaybackFinished) result).getPlayback());
					}

				}
			});

		



		} catch (Throwable t) {
			t.printStackTrace();
		}

	}

	private static CompletableFuture<Object> play(ARI ari, AtomicReference<StasisStart> s) {

		Channel currChannel = s.get().getChannel();
		String channID = currChannel.getId();

		CompletableFuture<Object> res = new CompletableFuture<Object>();

		ari.channels().play(channID, "sound:hello-world", currChannel.getLanguage(), 0, 0, UUID.randomUUID().toString() ,
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
						// set and say to witch list in the hash (by name?) Receiving: (id, future)
						setLst("play", pbID, res);
					}

				});
		return res;
	}

	private static void CreateList() {
		// TODO Auto-generated method stub

		Map<String, CompletableFuture<Object>> synchronizedMapPlay = Collections
				.synchronizedMap(new HashMap<String, CompletableFuture<Object>>());
		Map<String, CompletableFuture<Object>> synchronizedMapSay = Collections
				.synchronizedMap(new HashMap<String, CompletableFuture<Object>>());

		verbs.put("play", synchronizedMapPlay);
		verbs.put("say", synchronizedMapSay);

	}

	public static void setLst(String lstName, String id, CompletableFuture<Object> future) {

		// add to list lstName (for example play) new entry with id and future
		verbs.get(lstName).put(id, future);

	}

	public static CompletableFuture<Object> getFuture(String lstName, String id) {
		// remove
		verbs.get(lstName).remove(id);		
		// get the future according to the id, in the relevant lstName
		return verbs.get(lstName).get(id);
	}


	private static void voiceApp(ARI ari, AtomicReference<StasisStart> ss) {
		CompletableFuture<Void> cf = CompletableFuture.completedFuture(null);
		String channID = ss.get().getChannel().getId();
		
		try {
			
			ari.channels().answer(channID);
		} catch (RestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//play (complete somehow that the user will call play)
		play(ari, ss).thenAccept(pb ->{
			Playback p = (Playback) pb;
			logger.info("finished playback! id: " + p.getId());	
			// hangup call
			logger.info("hangup my call");
			try {
				ari.channels().hangup(channID, "normal");
			} catch (RestException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
	}

}

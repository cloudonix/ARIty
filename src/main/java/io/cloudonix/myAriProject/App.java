package io.cloudonix.myAriProject;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.ActionAsterisk;
import ch.loway.oss.ari4java.generated.ActionChannels;
import ch.loway.oss.ari4java.generated.ActionEvents;
//import ch.loway.oss.ari4java.cfg.AriVersion;
import ch.loway.oss.ari4java.generated.AsteriskInfo;
import ch.loway.oss.ari4java.generated.Channel;
import ch.loway.oss.ari4java.generated.Message;
import ch.loway.oss.ari4java.generated.Playback;
import ch.loway.oss.ari4java.generated.PlaybackFinished;
import ch.loway.oss.ari4java.generated.StasisStart;
import ch.loway.oss.ari4java.tools.ARIException;
import ch.loway.oss.ari4java.tools.AriCallback;
import ch.loway.oss.ari4java.tools.RestException;
import ch.loway.oss.ari4java.AriFactory;
import ch.loway.oss.ari4java.AriVersion;

/**

 *
 */
public class App {
	private final static Logger logger = Logger.getLogger(App.class.getName());

	public static void main(String[] args) {

		AtomicReference<StasisStart>  ss = new AtomicReference<StasisStart>(null);
		CountDownLatch latchStasis = new CountDownLatch(1);
		CountDownLatch latchPlaybackEnd = new CountDownLatch(1);
				
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

					if(result instanceof StasisStart) {
						//StasisStart case
						ss.set((StasisStart) result);
						latchStasis.countDown();
						
					}
					else if(result instanceof PlaybackFinished) {
						// PlaybackFinished case
						latchPlaybackEnd.countDown();
						
					}
					
					//every thing else if we get here
					//logger.info("not stasis and not PlaybackFinished");
					
				/*	else(!(result instanceof StasisStart)) {
						logger.info(result.toString());
						// if result is not StasisStart, ignore it
						return;

					}*/
					
						
					// ---------------------- to complete!!

				}
			});

			// result is StasisStart
			
			// StasisStart event = (StasisStart) ss;
			latchStasis.await();
			Channel currChannel = ss.get().getChannel();
			String channID = currChannel.getId();
			logger.info("the channel id is:" + channID);

			// answer the call
			
			ari.channels().answer(channID);

				/*
				 * // play sync Playback pb = ari.channels().play(channID, "sound:hello-world",
				 * currChannel.getLanguage(), 0, 0, ""); logger.info("play" +
				 * pb.getMedia_uri());
				 */

				// play ansync
				// CountDownLatch loginLatch = new CountDownLatch(1);
			
			
			
				AtomicReference<Playback> pb = new AtomicReference<Playback>(null);

				ari.channels().play(channID, "sound:hello-world", currChannel.getLanguage(), 0, 0, "",
						new AriCallback<Playback>() {

							@Override
							public void onFailure(RestException e) {
								// TODO Auto-generated method stub
								e.printStackTrace();
							}

							@Override
							public void onSuccess(Playback resultM) {

								if (!(resultM instanceof Playback))
									return;
								pb.set(resultM);
							}
						});

				// hangup call
				latchPlaybackEnd.await();
				logger.info("hangup my call");
				ari.channels().hangup(channID, "normal");
				
				/*try {
					Thread.sleep(TimeUnit.SECONDS.toMillis(10));
					ari.channels().hangup(channID, "normal");
				} catch (RestException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/

			

		} catch (Throwable t) {
			t.printStackTrace();
		}

		/*while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				logger.info("thread is not sleeping");
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}*/
	}
}

package io.cloudonix.myAriProject;

import java.util.List;
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

		try {

			ARI ari = AriFactory.nettyHttp("http://127.0.0.1:8088/", "userid", "secret", AriVersion.ARI_2_0_0);
			ari.events().eventWebsocket("stasisAPP", true, new AriCallback<Message>() {

				@Override
				public void onSuccess(Message result) {
					String as_id = result.getAsterisk_id();
					logger.info("success! Asterisk id: " + as_id);

					if (!(result instanceof StasisStart))
						// if result is not StasisStart, ignore it
						return;

					// result is StasisStart
					StasisStart event = (StasisStart) result;
					Channel currChannel = event.getChannel();
					String channID = currChannel.getId();
					logger.info("the channel id is:" + channID);

					// answer the call
					try {
						ari.channels().answer(channID);
						
						// play syn
						Playback pb = ari.channels().play(channID, "sound:hello-world", currChannel.getLanguage(), 0, 0, "");
						logger.info("play" + pb.getMedia_uri());
						
						/*// play ansync
						ari.channels().play(channID, "sound:hello-world",currChannel.getLanguage(), 0, 0, new AriCallback<Playback>() {

							@Override
							public void onSuccess(Playback resultM) {
								
								if(!(resultM instanceof Playback))
									return;
								
								// hangup call
								logger.info("hangup my call");
								try {
									ari.channels().hangup(channID, "call ending");
								} catch (RestException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

							}

							@Override
							public void onFailure(RestException e) {
								// TODO Auto-generated method stub
								e.printStackTrace();
							}
							
						});

						*/

					} catch (RestException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ARIException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}

				@Override
				public void onFailure(RestException e) {
					// TODO Auto-generated method stub
					e.printStackTrace();
				}
			});

			/*
			 * //AsteriskInfo info = ari.asterisk().getInfo(""); List<Channel> channels =
			 * ari.channels().list(); System.out.println("There are " + channels.size() +
			 * " active channels now."); //System.out.println("System up since " +
			 * info.getStatus().getStartup_time());
			 * 
			 * // Get the implementation for a ActionChannels interface ActionChannels
			 * action = ari.getActionImpl(ActionChannels.class);
			 * 
			 * 
			 * // ari.channels().answer(ari.getAppName());
			 * 
			 * for(int i = 0; i< channels.size(); i++) { // answer the channel
			 * action.answer(channels.get(i).getId()); //play
			 * action.play(channels.get(i).getId(), media, lang, offsetms, skipms);
			 * 
			 * 
			 * }
			 */

		} catch (Throwable t) {
			t.printStackTrace();
		}

		while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				logger.info("thread is not sleeping");
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}

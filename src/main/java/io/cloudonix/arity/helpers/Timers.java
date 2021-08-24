package io.cloudonix.arity.helpers;

import java.util.Timer;
import java.util.TimerTask;

public class Timers {

	private static Timer myTimer = new Timer("arity-timer", true);

	public static void schedule(Runnable action, long delay) {
		myTimer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				action.run();
			}
		}, delay);
	}


}

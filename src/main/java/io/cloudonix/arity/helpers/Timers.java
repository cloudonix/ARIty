package io.cloudonix.arity.helpers;

import java.util.Timer;
import java.util.TimerTask;

public class Timers {

	private static Timer myTimer = new Timer("arity-timer", true);

	public static TimerTask schedule(Runnable action, long delay) {
		var newTask = new TimerTask() {
			
			@Override
			public void run() {
				action.run();
			}
		};
		myTimer.schedule(newTask, delay);
		return newTask;
	}


}

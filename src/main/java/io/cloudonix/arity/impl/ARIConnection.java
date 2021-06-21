package io.cloudonix.arity.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.cloudonix.arity.CallState;

public abstract class ARIConnection {

	protected ExecutorService asyncExecutor;

	public static enum Version {
		ARI_0_0_1("0.0.1"), ARI_1_0_0("1.0.0"), ARI_1_1_0("1.1.0"), ARI_1_2_0("1.2.0"), ARI_1_3_0("1.3.0"),
		ARI_1_4_0("1.4.0"), ARI_1_5_0("1.5.0"), ARI_1_6_0("1.6.0"), ARI_1_7_0("1.7.0"), ARI_1_8_0("1.8.0"),
		ARI_1_9_0("1.9.0"), ARI_1_10_0("1.10.0"), ARI_1_10_1("1.10.1"), ARI_1_10_2("1.10.2"), ARI_1_11_2("1.11.2"),
		ARI_2_0_0("2.0.0"), ARI_3_0_0("3.0.0"), ARI_4_0_0("4.0.0"), ARI_4_0_1("4.0.1"), ARI_4_0_2("4.0.2"),
		ARI_4_1_2("4.1.2"), ARI_4_1_3("4.1.3"), ARI_5_0_0("5.0.0"), ARI_5_1_0("5.1.0"), ARI_5_1_1("5.1.1"),
		ARI_6_0_0("6.0.0"), ARI_7_0_0("7.0.0"),
		LATEST("");

		private String version;

		Version(String version) {
			this.version = version;
		}
		
		public String toString() {
			return version;
		}
	}

	public void setExecutorService(ExecutorService asyncExecutor) {
		this.asyncExecutor = asyncExecutor;
	}
	
	public abstract CompletableFuture<Void> connect(Version version);
	public abstract CompletableFuture<CallState> getCallState(String channelId);
}

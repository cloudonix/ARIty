# ARIty Framework
ARIty is a library that gives you services to run voice applications, using the Asterisk ARI protocol.

Arity exposes the ARI API using a set of asynchronous operations, using java
`[CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)` and fluent API concepts.

## Sample Voice Application

```
public class Application extends CallController {

	// Application class extends CallController, which includes an abstract method 'run()', and therefore need to implements
	// it

	@Override
	public CompletableFuture<Void> run() {
			return CompletableFuture.completedFuture(null);
	}

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {

		// Connect to ARI and register a stasis application
		ARIty arity = null;
		try {
			arity = new ARIty("http://127.0.0.1:8088/", "myStasisApp", "user", "pass");

		} catch (Throwable e1) {
			logger.info("Error When creating the ARIty: " + e1.getMessage());
		}

		// main application flow
	     arity.registerVoiceApp(call -> {
			call.answer().run()
			.thenCompose(v -> call.play("hello-world").loop(2).run())
			.thenCompose(pb -> {
				logger.info("finished playback! id: " + pb.getPlayback().getId());
				return call.hangup().run();
			}).handle(call::endCall).exceptionally(t -> {
				logger.severe(t.toString());
				return null;
			});
		});

        // after registering, just stay running to get and handle calls
		while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Thread is not sleeping");
			}
		}
	}

}
```

## Installation

ARIty is available from Jitpack. To use it, add the Jitpack repository to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

Then add the ARIty dependency to the dependencies list:

```xml
<dependency>
  <groupId>com.github.cloudonix</groupId>
  <artifactId>arity</artifactId>
  <version>0.12.0</version>
</dependency>
```

## Usage

To use ARIty, we start by creating a connection to Asterisk's ARI URL and registering our Stasis application.
ARIty will trigger the application entry point for each incoming call providing a `CallController` API using which the application
can manipulate the call state and perform channel-specific operation, such as playback and record.

### Connecting

```
String url = "http://asterisk:8088/";
ARIty ari = new ARIty(url, "myStasisApp", "user", "pass");
```
### Registering to receive incoming calls

The `registerVoiceApp()` method receives a "callable" value (functional interface) that accepts a `CallController` object and executes
the implementation for each call sent to the Stasis application by Asterisk.

Please note that currently only one voice application may be registered.

In order to register your voice application, you have the following options:

#### Give a lambda during registration:

```

public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {

	...
		arity.registerVoiceApp(call -> {
				// handle call
				call.dial("SIP/app2").run().handle(call::endCall)
				.exceptionally(t -> {
					logger.severe(t.toString());
					return null;
				});

		});

```

#### Give a `Supplier` of `CallController`:

```
	public void voiceApp(CallController call) {
		// handle call
		...
	}

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
	    Application app = new Application();
	...
		ari.registerVoiceApp(app::voiceApp);
	...
	}
```
#### Give an instance of your class (for example: `Application.class`):

```
	@Override
	public void run() {
		// handle call
		...
	}

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
	...
		arity.registerVoiceApp(Application.class);
	...

	}
```

### Handling a call
To handle a call scenario, create a method that takes a `CallController` argument. When the method is called, use the `CallController`
API to execute operations on the channel connected to the application. Each API call creates an `Operation` instance that will start
the specified operation when it's `run()` method is called. `run()` returns a `CompletableFuture` that will be completed when the ARI
operation completes. Multiple operation can be scheduled in order, using `CompletableFuture`'s completion methods, such as
`thenCompose()`, `thenAccept()`, etc.

```
	public void voiceApp(CallController call) {

		call.answer().run()
		.thenCompose(ans -> call.play("hello-world").loop(2).run())
		.thenCompose(play -> {
			logger.info("finished playback! id: " + play.getPlayback().getId());
			return call.hangup().run();
		}).thenAccept(h -> {
			logger.info("hanged up call");
		}).exceptionally(t -> {
			logger.severe("Unexpected error happened", t);
			return null;
		});
	}

```
## Features

### endCall feature
In order to end the voice application, you can use the `endCall` method that will guarantee that the call will be hanged up (you
can see an example in "Sample Voice Application" section above). This method hang up the call anyway, even if an error occurred
during the execution.
Therefore, you can use the `endCall` method to hang up the call and/or use `call.hangup().run();` like in the "Handling call" example.


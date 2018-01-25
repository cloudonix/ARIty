# ARIty Framework
ARIty is a library that gives you services to run voice applications, using the Asterisk ARI protocol. 

Arity exposes the ARI API using a set of asynchronous operations, using java
`[CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)` and fluent API concepts.

## Sample Voice Application

```
public class Application {

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		
		// Connect to ARI and register a stasis application
		new ARIty("http://127.0.0.1:8088/", "myStasisApp", "user", "pass").registerVoiceApp(call -> {
		
	      // main application flow
		   call.answer().run()
				.thenCompose(v -> call.play("hello-world").loop(2).run())
				.thenAccept(pb -> {
					logger.info("finished playback! id: " + pb.getPlayback().getId());
				}).handle(call::endCall)
				.exceptionally(t -> {
					logger.severe("Unexpected error happened");
					return null;
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

In your `pom.xml` file, add the repository for ARIty (we are currently not hosted in the public Maven repository) as an element under `<project>`:

```
<repositories>
  <repository>
    <id>cloudonix-dist</id>
    <url>http://cloudonix-dist.s3-website-us-west-1.amazonaws.com/maven2/releases</url>
  </repository>
</repositories>
```

Then add ARIty as a dependency:

```
<dependency>
	<groupId>tech.greenfield</groupId>
	<artifactId>arity</artifactId>
	<version>[0,)</version>
</dependency>
```

## Usage

To use ARIty, we start by creating a connection to Asterisk's ARI URL and registering our Stasis application.
ARIty will trigger the application entry point for each incoming call providing a `Call` API using which the application can manipulate
the call state and perform channel-specific operation, such as playback and record.

### Connecting 

```
String url = "http://asterisk:8088/";
ARIty ari = new ARIty(url, "myStasisApp", "user", "pass");
```
### Registering to receive incoming calls

```
	public void voiceApp(Call call) {
		// handle call 
		...
	}
	
	...
		ari.registerVoiceApp(app::voiceApp);
	...
```

The `registerVoiceApp()` method receives a "callable" value (functional interface) that accepts a `Call` object and executes the 
implementation for each call sent to the Stasis application by Asterisk. 

Please note that currently only one voice application may be registered. 

### Handling a call
To handle a call scenario, create a method that takes a `Call` argument. When the method is called, use the `Call` API to execute
operations on the channel connected to the application. Each API call creates an `Operation` instance that will start the specified 
operation when it's `run()` method is called. `run()` returns a `CompletableFuture` that will be completed when the ARI operation 
completes. Multiple operation can be scheduled in order, using `CompletableFuture`'s completion methods, such as `thenCompose()`,
`thenAccept()`, etc.

```
	public void voiceApp(Call call) {

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
 

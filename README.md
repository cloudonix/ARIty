# ARIty Framework
ARIty is a library that gives you services of a call, using ari4java. You will be able to write your voice applications using ARIty library to
execute them.

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
	<groupId>io.cloudonix</groupId>
	<artifactId>arity</artifactId>
	<version>[0,)</version>
</dependency>

```

## Usage
Under ARIty we use the concept of "Application"- a class whose fields and methods are used as handlers for incoming/outgoing calls.

The main method of the class will create the ARIty service and will register your voice application, in order to run it.
Your voice application will use the functionality of CompletableFuture , you can read about it in the following link:
https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html

### Simple Voice Application
To handle a call scenario, create your voice application and define the actions that you want them to happen. You can create one or more voice application, each when that will have a different scenario.

### A sample of voice application
An example of a voice application that answers the call, then play a sound file 3 times, then gathers the input from the user until he presses the terminating key (for example: #) and then hang up the call.

```
public Class Application {

	public void voiceApp(Call call) {
		call.answer().run().thenCompose(v -> call.play("hello-world").loop(3).run())
				.thenAccept(pb -> logger.info("finished playback! id: " + pb.getId()))
				.thenCompose(g -> call.gather("#").run()).thenCompose(d -> {
					logger.info("gather is finished");
					return call.hangUp().run();
				}).thenAccept(h -> {
					logger.info("hanged up call");
				}).exceptionally(t -> {
					logger.severe(t.toString());
					return null;
				});
	}
}

```

## Initializing 
In order to create the ARIty service, you need to create a main method in the Application class. In the main method, you need to do the following:

1.Create a new instance of the Application class.

2.Create and initialize with the following parameters: URI, the name of the stasis application, user name and password.

3.Register your voice application, using the library method "registerVoiceApp" and give it the name of your voice application as argument.

4.Add a loop to avoid exiting from the application.

### Simple example
```
public class Application {

	private static String URI = "http://127.0.0.1:8088/";

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		Application app = new Application();
		// Create the service of ARI
		ARIty ari = null;
		try {
			ari = new ARIty(URI, "stasisApp", "userid", "secret");

		} catch (Throwable e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		ari.registerVoiceApp(app::voiceApp);

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


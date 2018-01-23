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
	<groupId>tech.greenfield</groupId>
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

## Initializing 
In order to create the ARIty service, you need to create a main method in the Application class. In the main method, you need to do the following:

1. Create a new instance of the Application class. 
2. Create and initialize with the following parameters: URI, the name of the stasis application, user name and password.
3. Register your voice application, using the library method "registerVoiceApp" and give it the name of your voice application as argument.
4. Add a loop to avoid exiting from the application.

### Simple example

```
public class Application {

	public static void main(String[] args) throws ConnectionFailedException, URISyntaxException {
		Application app = new Application();
		// Connect to ARI and register a stasis application
		new ARIty("http://127.0.0.1:8088/", "myStasisApp", "user", "pass").registerVoiceApp(call -> {
	        // main application flow
		    call.play("hello").run()
				.thenCompose(p -> call.gather().and(call.play("dir-pls-enter").loop(10)).run())
				.thenCompose(g -> {
					logger.info("User entered: " + g.allInputGathered());
					return call.hangUp().run();
				}).exceptionally(t -> {
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

FROM openjdk
COPY target/classes/ /app
CMD java -cp /app/ io.cloudonix.myAriProject.App

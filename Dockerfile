FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/agile-savannah-81249-0.0.1-SNAPSHOT-standalone.jar /agile-savannah-81249/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/agile-savannah-81249/app.jar"]

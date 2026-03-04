# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jre AS run
WORKDIR /work/

# Copy Quarkus fast-jar output
COPY target/quarkus-app/lib/ ./lib/
COPY target/quarkus-app/*.jar ./
COPY target/quarkus-app/app/ ./app/
COPY target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0"
CMD ["sh", "-c", "java $JAVA_OPTS -jar quarkus-run.jar"]

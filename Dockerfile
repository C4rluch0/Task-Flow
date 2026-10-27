# syntax=docker/dockerfile:1

# ---------- Stage 1: build the WAR with Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy only the pom first: Docker caches the downloaded dependencies
# and re-downloads them only when pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:resolve

# Now the source code. Tests are skipped here; run them with "mvn test" while developing.
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Stage 2: run it on Tomcat 11 ----------
# This final image has no Maven, no JDK and no source code: only what is needed to run.
FROM tomcat:11.0-jre21-temurin-jammy

# Tomcat deploys any WAR placed in webapps. The file name defines the URL: /TaskFlow/
COPY --from=build /build/target/TaskFlow.war /usr/local/tomcat/webapps/TaskFlow.war

# The H2 database file lives in /data so it can be kept in a volume
# (Database.java reads this variable).
ENV TF_DB_PATH=/data/taskflow
RUN mkdir -p /data
VOLUME /data

EXPOSE 8080
# The base image already starts Tomcat (catalina.sh run).

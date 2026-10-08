# The Self-hosted edition's backend, built from backend/: docker build -f selfhosted-app/Dockerfile backend
# Parity builds pin these to digests.
ARG JDK_IMAGE=eclipse-temurin:26-jdk
ARG JRE_IMAGE=eclipse-temurin:26-jre

# Tests are not run here: the selfhosted-edition CI job runs them, and an image build has no Docker daemon for
# Testcontainers. The jar is the same bytecode for every platform, so this stage runs on the builder's own and only
# the runtime stage below is built once per platform of a multi-platform image.
FROM --platform=$BUILDPLATFORM ${JDK_IMAGE} AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY core/pom.xml core/
COPY selfhosted-app/pom.xml selfhosted-app/
RUN ./mvnw -B -pl selfhosted-app -am dependency:go-offline

COPY core/src/ core/src/
COPY selfhosted-app/src/ selfhosted-app/src/
# -DskipTests, not -Dmaven.test.skip: selfhosted-app resolves core's test-jar, which the latter would not build.
RUN ./mvnw -B -pl selfhosted-app -am clean package -DskipTests

FROM ${JRE_IMAGE} AS runtime
WORKDIR /app

# A numeric user, so a platform that requires a non-root user can check it without reading /etc/passwd.
RUN groupadd --system --gid 10001 axonia \
	&& useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin axonia \
	&& mkdir -p /var/lib/axonia/media \
	&& chown 10001:10001 /var/lib/axonia/media

# Owned by root, so the process can read its code but never write it.
COPY --from=build /build/selfhosted-app/target/axonia-selfhosted-1.0-SNAPSHOT.jar app.jar

USER 10001:10001

# Images land here unless APP_MEDIA_TYPE names a bucket or a container; mount a volume at this path.
ENV MEDIA_LOCAL_PATH=/var/lib/axonia/media
# The JVM sizes its heap from the container's memory limit; JAVA_TOOL_OPTIONS replaces this whole line.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

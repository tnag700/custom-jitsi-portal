FROM eclipse-temurin:25-jdk@sha256:119a3d18f160a3e7655a66034d0f43beee31cd7b3b9142d57a5de29772011de6 AS build

WORKDIR /workspace

COPY . .

RUN sed -i 's/\r$//' gradlew \
	&& chmod +x gradlew \
	&& ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre@sha256:8da0490fa9a3c26867012019565948eef0ee69438f5c75ac28146967bae984b5

WORKDIR /app

RUN apt-get update \
	&& apt-get install -y curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& groupadd --system --gid 10001 appuser \
	&& useradd --system --uid 10001 --gid 10001 --create-home --home-dir /app --shell /usr/sbin/nologin appuser

COPY --from=build --chown=10001:10001 /workspace/build/libs/*.jar /app/app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY . .

RUN sed -i 's/\r$//' gradlew \
	&& chmod +x gradlew \
	&& ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
	&& apt-get install -y curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& groupadd --system --gid 10001 appuser \
	&& useradd --system --uid 10001 --gid 10001 --create-home --home-dir /app --shell /usr/sbin/nologin appuser

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

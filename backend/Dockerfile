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
	&& rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

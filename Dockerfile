FROM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:57424b1d8eca9c4cf8a4fe226207027911f08d6758628891bf59fcfb53777029 AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:cd35bb33859641c5374415826a5951845104093eaa401cbe9d3d9023444f1c13
RUN useradd --system --no-create-home --uid 10001 trading
WORKDIR /app
COPY --from=build /workspace/target/trading-api-*.jar app.jar
USER trading
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
COPY database/ database/
RUN ./mvnw -q -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system finledger \
    && useradd --system --gid finledger --no-create-home finledger
WORKDIR /app
COPY --from=build --chown=finledger:finledger /workspace/target/finledger-*.jar app.jar

USER finledger
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

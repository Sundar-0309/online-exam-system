FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' ./mvnw \
    && chmod +x ./mvnw \
    && ./mvnw -B -ntp dependency:go-offline

COPY src/ ./src/

RUN ./mvnw -B -ntp clean package

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

ENV PORT=10000
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 10000

RUN mkdir -p /app/uploads \
    && chown 10001:10001 /app/uploads

COPY --from=build --chown=10001:10001 \
    /workspace/target/online-exam-0.0.1-SNAPSHOT.jar /app/app.jar

USER 10001:10001

ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-10000} -jar /app/app.jar"]

FROM eclipse-temurin:25-jre@sha256:4ed132f6832e9804b123e89a849a7b4502756ae64a4ee08ebab939d9d5d0a9fa AS runtime
ARG REVISION
LABEL org.opencontainers.image.revision=$REVISION
WORKDIR /app
COPY app.jar /app/app.jar
COPY --chmod=0555 healthcheck.sh /usr/local/bin/curricula-healthcheck
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]

FROM runtime AS api
COPY --chown=10001:10001 .volume-root /var/lib/curricula/rdf/
EXPOSE 8081

FROM runtime AS web
EXPOSE 8080

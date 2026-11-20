FROM eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6 AS runtime
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

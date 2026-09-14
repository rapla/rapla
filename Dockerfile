FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="Rapla" \
      org.opencontainers.image.description="Resource scheduling and event planning (Spring Boot fat JAR)" \
      org.opencontainers.image.source="https://github.com/rapla/rapla" \
      org.opencontainers.image.url="https://rapla.org" \
      org.opencontainers.image.documentation="https://github.com/rapla/rapla/blob/master/docs/deployment.md#docker" \
      org.opencontainers.image.vendor="Rapla Team" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later OR Apache-2.0"

WORKDIR /opt/rapla

RUN groupadd --system --gid 999 rapla \
    && useradd --system --uid 999 --gid rapla --home-dir /opt/rapla --shell /usr/sbin/nologin rapla \
    && mkdir config data lib plugins logs work \
    && chown rapla:rapla data logs work

COPY rapla-app/target/rapla.jar rapla.jar

USER rapla

VOLUME ["/opt/rapla/data", "/opt/rapla/logs"]

EXPOSE 8051

ENTRYPOINT ["java", "-jar", "rapla.jar"]

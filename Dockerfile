FROM eclipse-temurin:21-jre

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

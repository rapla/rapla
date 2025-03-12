FROM alpine:3.18

WORKDIR /app

RUN apk add --no-cache \
    openjdk11-jre \
    tar

COPY target/distribution/*.tar.gz /app/rapla_package.tar.gz
RUN tar -xzvf rapla_package.tar.gz

COPY target/*.war /app/webapps/rapla.war

COPY data/data.xml /app/data/data.xml

COPY rapla.ks /app/rapla.ks

COPY src/test/etc/jetty.xml /app/etc/jetty.xml

CMD ./raplaserver.sh run
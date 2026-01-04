FROM alpine:3.23

WORKDIR /opt/rapla

RUN apk add --no-cache \
    openjdk21-jre \
    tar

COPY target/distribution/*.tar.gz /opt/rapla/rapla_package.tar.gz
RUN tar -xzvf rapla_package.tar.gz

CMD ./raplaserver.sh run

EXPOSE 8051
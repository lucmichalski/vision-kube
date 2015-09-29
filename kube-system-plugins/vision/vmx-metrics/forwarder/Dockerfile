FROM golang:1.5-wheezy
MAINTAINER Helder Correia <heldercorreia@morfose.net>

# Build latest version from master
RUN git clone https://github.com/elasticsearch/logstash-forwarder.git /opt/logstash-forwarder
WORKDIR /opt/logstash-forwarder
RUN go build

# Run installer
RUN mkdir -p /target/
RUN cp /opt/logstash-forwarder/logstash-forwarder /target/logstash-forwarder

ADD config.json /etc/logstash-forwarder/config.json
# Add logstash-forwarder key and certificate
ADD ssl/logstash-forwarder.key /etc/logstash/logstash-forwarder.key
ADD ssl/logstash-forwarder.crt /etc/logstash/logstash-forwarder.crt

RUN mkdir -p /opt/logs

CMD [ "/opt/logstash-forwarder/logstash-forwarder", "-config", "/etc/logstash-forwarder/config.json"]

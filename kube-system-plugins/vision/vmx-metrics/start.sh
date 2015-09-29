#!/bin/bash
openssl req -x509  -batch -nodes -newkey rsa:2048 \
-keyout logstash/conf/logstash-forwarder.key \
-out logstash/conf/logstash-forwarder.crt \
-subj /CN=logstash
cp logstash/conf/logstash-forwarder.crt forwarder/ssl/logstash-forwarder.crt
cp logstash/conf/logstash-forwarder.key forwarder/ssl/logstash-forwarder.key
docker-compose stop
docker-compose build
docker-compose up -d
docker-compose logs forwarder

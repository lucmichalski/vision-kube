FROM dduportal/docker-compose:latest
MAINTAINER your.mail@here
ENV DOCKER_HOST=tcp://127.0.0.1:2376
ADD . /app/
RUN openssl req -x509  -batch -nodes -newkey rsa:2048 \
-keyout /app/logstash/conf/logstash-forwarder.key \
-out /app/logstash/conf/logstash-forwarder.crt \
-subj /CN=logstash
RUN cp /app/logstash/conf/logstash-forwarder.crt /app/forwarder/ssl/logstash-forwarder.crt
RUN cp /app/logstash/conf/logstash-forwarder.key /app/forwarder/ssl/logstash-forwarder.key
ENTRYPOINT ["/usr/local/bin/docker-compose"]
CMD ["--version"]

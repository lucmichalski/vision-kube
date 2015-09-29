FROM quay.io/pires/docker-jre:8u60
MAINTAINER pjpires@gmail.com

# Export Lumberjack
EXPOSE 5043

ENV LOGSTASH_PKG_NAME logstash-1.5.4

# Add stuff
RUN apk add --update curl bash ca-certificates

# Install Logstash
RUN \
  ( curl -Lskj http://download.elastic.co/logstash/logstash/$LOGSTASH_PKG_NAME.tar.gz | \
  gunzip -c - | tar xf - ) && \
  mv $LOGSTASH_PKG_NAME /logstash && \
  rm -rf $(find /logstash | egrep "(\.(exe|bat)$|sigar/.*(dll|winnt|x86-linux|solaris|ia64|freebsd|macosx))") && \
  apk del curl wget

# Logstash config
VOLUME ["/logstash/config"]

# Optional certificates folder for logstash-forwarder
VOLUME ["/logstash/certs"]

CMD ["/logstash/bin/logstash", "--quiet",  "-f", "/logstash/config/logstash.conf"]

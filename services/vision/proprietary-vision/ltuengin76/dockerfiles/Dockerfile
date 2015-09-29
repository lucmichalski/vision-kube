FROM centos:centos6
MAINTAINER Luc Michalski <luc.michalski@blippar.com>

RUN yum update -y; yum clean all
RUN yum -y install epel-release; yum clean all
RUN yum -y install unzip git python-setuptools python-dateutil python-magic createrepo cronie jq wget vim; yum clean all

RUN git clone https://github.com/s3tools/s3cmd.git /s3cmd
RUN cd /s3cmd && python setup.py install
ADD s3cfg /.s3cfg

ENV BUCKET_NAME vs-bucket-test-markers
ENV ACCESS_KEY  AKIAJX5PMOG2GMVC3RUA
ENV SECRET_KEY  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

RUN mkdir -p /opt/datasets
RUN mkdir -p /opt/data
RUN mkdir -p /opt/ltuengine/core
ADD fetch.sh /fetch.sh
RUN chmod +x /fetch.sh
RUN /fetch.sh
RUN rm /opt/ltu.zip
RUN ls -l
RUN cd /opt/ltu-engine-7.6.3 && ls -l && ./install.sh /opt/ltuengine76; echo ok

EXPOSE 1979
EXPOSE 1980

WORKDIR /opt/ltuengine76/
RUN wget https://github.com/EmileVauge/traefik/releases/download/v1.0/traefik && pwd && ls -l && \
    mv ./traefik ./traefik_d && \
    chmod +x ./traefik_d

ADD traefik.toml /opt/ltuengine76/traefik.toml
ADD router.sh /opt/ltuengine76/router.sh
RUN chmod +x /opt/ltuengine76/router.sh

ADD crontab /etc/cron.d/ltud
RUN chmod 0644 /etc/cron.d/ltud
RUN touch /var/log/cron.log
RUN crond

CMD ["/opt/ltuengine76/traefik_d","/opt/ltuengine76/traefik.toml"]

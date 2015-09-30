FROM centos:centos6
MAINTAINER Luc Michalski <luc.michalski@blippar.com>

RUN yum update -y; yum clean all
RUN yum -y install epel-release unzip git python-setuptools python-dateutil python-magic wget createrepo; yum clean all
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
RUN cat /opt/ltu-engine-7.6.3/licence.lic
RUN cat /etc/hosts
RUN cd /opt/ltu-engine-7.6.3 && ls -l && ./install.sh /opt/ltuengine76; echo ok

ADD rules.toml /rules.toml

EXPOSE 8080
EXPOSE 7789
EXPOSE 8888
EXPOSE 7791
VOLUME /opt/ltuengine76/logs
VOLUME /opt/ltuengine76/config
VOLUME /opt/ltuengine76/run/cache

ADD traefik.toml /opt/traefik.toml
ADD rules.toml /opt/rules.toml

RUN wget https://github.com/EmileVauge/traefik/releases/download/v1.0/traefik && pwd && ls -l && \
    mv ./traefik ./traefik_d && \
    chmod +x ./traefik_d && \
    mv ./traefik_d /opt/traefik_d
CMD ["/opt/traefik_d","/opt/traefik.toml"]

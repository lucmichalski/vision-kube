FROM ubuntu
MAINTAINER Luc Michalski luc.michalki@blippar.com

#Setup vmx-environment
RUN apt-get update && apt-get -y install libgmp10 libxt6 libxext6 libxmu6 build-essential wget tar unzip nginx libpcre3-dev libpcre++-dev openssl libssl-dev libxml2-dev python-lxml  libxslt1-dev python-dev jd nano cron screen

WORKDIR /usr/local/bin
RUN ["wget", "-Oconfd", "https://github.com/kelseyhightower/confd/releases/download/v0.10.0/confd-0.10.0-linux-amd64"]
RUN ["chmod", "+x", "confd"]

WORKDIR /

###INSTALL MATLAB Runtime
RUN mkdir -p /root/matlab_installer
ADD matlab_options.txt /root/matlab_installer/matlab_options.txt

WORKDIR /root/matlab_installer
RUN wget -nc http://www.mathworks.com/supportfiles/downloads/R2014a/deployment_files/R2014a/installers/glnxa64/MCR_R2014a_glnxa64_installer.zip && \
    unzip MCR_R2014a_glnxa64_installer.zip && \
    ./install -inputFile matlab_options.txt && \
    rm -rf /root/matlab_installer && \
    rm -f MCR_R2014a_glnxa64_installer.zip

#VOLUME /root/MATLAB

#################  vmx middle

RUN mkdir -p /vmx/middle
WORKDIR /vmx

RUN \ 
  wget -q http://files.vision.ai/vmx/VMXmiddle/Linux/VMXmiddle_Linux_v0.4.0-7-g0275866.tar.gz && \
  cd middle && \                                                         
  gzip -d ../VMXmiddle_Linux_v0.4.0-7-g0275866.tar.gz && \
  tar xf ../VMXmiddle_Linux_v0.4.0-7-g0275866.tar && \
  rm ../VMXmiddle_Linux_v0.4.0-7-g0275866.tar && \
  echo "Version is " `cat version`

#VOLUME /vmx/middle

###################### vmx-server

RUN mkdir -p /vmx/build
#VOLUME /vmx/build

WORKDIR /vmx

RUN apt-get -y install git python-setuptools python-dateutil python-magic createrepo
RUN git clone https://github.com/s3tools/s3cmd.git /s3cmd
RUN cd /s3cmd && python setup.py install
ADD s3cfg /.s3cfg

ENV BUCKET_NAME vs-bucket-test-markers
ENV ACCESS_KEY  AKIAJX5PMOG2GMVC3RUA
ENV SECRET_KEY  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

RUN \
  wget -q http://files.vision.ai/vmx/VMXserver/Linux/VMXserver_Linux_v0.2.0-9-g8bb540c.tar.gz && \
  gzip -d VMXserver_Linux_v0.2.0-9-g8bb540c.tar.gz && \
  tar xf VMXserver_Linux_v0.2.0-9-g8bb540c.tar && \
  rm VMXserver_Linux_v0.2.0-9-g8bb540c.tar && \
  echo "Version is " `cat build/version` && \
  mkdir -p /vmx/data && \
  cd /vmx/data

ADD config.json /vmx/build/config.json
#VOLUME /vmx/data

#####################   appbuilder
RUN mkdir -p /vmx/middle
WORKDIR /vmx/middle
RUN \
  wget -q http://files.vision.ai/vmx/vmxAppBuilder/vmxAppBuilder_v0.2.0-7-g7cbae14.tar.gz &&\ 
  gzip -d vmxAppBuilder_v0.2.0-7-g7cbae14.tar.gz &&\
  tar xf vmxAppBuilder_v0.2.0-7-g7cbae14.tar &&\ 
  rm vmxAppBuilder_v0.2.0-7-g7cbae14.tar &&\
  wget -q http://files.vision.ai/vmx/VMXdocs/VMXdocs_v0.4.9.tar.gz &&\
  gzip -d VMXdocs_v0.4.9.tar.gz &&\
  tar xf VMXdocs_v0.4.9.tar -C static &&\
  rm VMXdocs_v0.4.9.tar
#VOLUME /vmx/middle/static

######################  user data
RUN mkdir -p /vmx/models

ADD fetch.sh /fetch.sh
RUN chmod +x /fetch.sh
RUN /fetch.sh

VOLUME /vmx/models
WORKDIR /vmx/middle

#Exposing this volume breaks the Kinematic install
#VOLUME /vmx/sessions

RUN wget https://github.com/EmileVauge/traefik/releases/download/v1.0/traefik && \
    mv traefik /vmx/build/traefik_d && \
    chmod +x /vmx/build/traefik_d

ADD traefik.toml /vmx/build/traefik.toml
ADD router.sh /vmx/build/router.sh
RUN chmod +x /vmx/build/router.sh

WORKDIR /

WORKDIR /vmx/build/

ADD config.json /vmx/build/config.json
RUN apt-get install -y jq curl
ADD create_sessions.sh /create_sessions.sh
ADD active_models.txt /vmx/build/active_models.txt

EXPOSE 3000
RUN chmod +x /create_sessions.sh

RUN mkdir -p /vmx/build/output && \
    chmod -Rf 755 /vmx/build/output && \
    ./router.sh

# Add our crontab file
ADD crons.conf /root/crons.conf

# Use the crontab file
RUN crontab /root/crons.conf

# Start cron
RUN cron

EXPOSE 1979
EXPOSE 1980


WORKDIR /vmx/build/
ADD start.sh /start.sh
RUN chmod +x /start.sh
#RUN /create_sessions.sh
CMD ["/start.sh"]

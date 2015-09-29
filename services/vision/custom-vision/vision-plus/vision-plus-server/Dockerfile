# This installs the latest development build of vision.ai's VMX server
# visit us on github, docker hub, and on the web at https://vision.ai
# 
# This is an experimental "concatenation" of the individual vision.ai
# containers we typically use.

FROM node:0.12.2
MAINTAINER Michalski Luc

#Setup vmx-environment
RUN apt-get update && apt-get -y install libgmp10 libxt6 libxext6 libxmu6

#start mcr
RUN \
  apt-get update && apt-get install -y wget tar unzip

###INSTALL MATLAB Runtime
RUN mkdir -p /root/matlab_installer
ADD matlab_options.txt /root/matlab_installer/matlab_options.txt

WORKDIR /root/matlab_installer
RUN wget -nc http://www.mathworks.com/supportfiles/downloads/R2014a/deployment_files/R2014a/installers/glnxa64/MCR_R2014a_glnxa64_installer.zip && \
    unzip MCR_R2014a_glnxa64_installer.zip && \
    ./install -inputFile matlab_options.txt && \
    rm -rf /root/matlab_installer
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
RUN chmod +x /fetch.sh && /fetch.sh

VOLUME /vmx/models
WORKDIR /vmx/middle

#Exposing this volume breaks the Kinematic install
#VOLUME /vmx/sessions

ADD dominantColors-restApi /dominantColors-restApi

WORKDIR /
RUN apt-get install -y git gcc libpng-dev libjpeg-dev libfftw3-dev make libavcodec-dev libavformat-dev libswscale-dev libdispatch-dev libev-dev libatlas-base-dev libblas-dev libgsl0-dev
RUN git clone https://github.com/liuliu/ccv.git

COPY make_ccv.sh /
RUN chmod +x /make_ccv.sh
RUN /make_ccv.sh
COPY run_ccv.sh /
EXPOSE 3005
EXPOSE 3000
EXPOSE 3350

WORKDIR /dominantColors-restApi
# Dominant Colors filter
RUN ln -s /usr/bin/nodejs /usr/bin/node && npm install -g node-gyp nan && npm install --unsafe-perm true
WORKDIR /vmx/middle

CMD ["/vmx/middle/vmx"]

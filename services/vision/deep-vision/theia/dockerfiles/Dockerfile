# Version 2.0.1
FROM tleyden5iwx/caffe-gpu-master
MAINTAINER Michael Bartoli <michael.bartoli@pomona.edu>

RUN apt-get update && apt-get upgrade -y
RUN apt-get -y install \
	python \
	build-essential \
	python-dev \
	python-pip \
	wget \
	unzip \
	ipython \
	git \
	perl \
	libatlas-base-dev \
	gcc \
	gfortran \
	g++i \
	xorg 
RUN apt-get install -f
RUN pip install numpy scipy

# clone repo
WORKDIR /home
RUN git clone https://github.com/mbartoli/theia
WORKDIR /home/theia

# run matlab binary files 

ENV JAVA_VERSION_MAJOR 8
ENV JAVA_VERSION_MINOR 60
ENV JAVA_VERSION_BUILD 27
ENV JAVA_PACKAGE       server-jre

ADD matlab.txt /mcr-install/matlab.txt


# Download and unarchive Oracle Java
RUN curl -jksSLH "Cookie: oraclelicense=accept-securebackup-cookie"\
  http://download.oracle.com/otn-pub/java/jdk/${JAVA_VERSION_MAJOR}u${JAVA_VERSION_MINOR}-b${JAVA_VERSION_BUILD}/${JAVA_PACKAGE}-${JAVA_VERSION_MAJOR}u${JAVA_VERSION_MINOR}-linux-x64.tar.gz \
    | tar -xzf - -C /opt &&\
    ln -s /opt/jdk1.${JAVA_VERSION_MAJOR}.0_${JAVA_VERSION_MINOR} /opt/jdk

# Install MatLab runtime
RUN \
	cd /mcr-install && \
	wget -nv http://de.mathworks.com/supportfiles/downloads/R2015b/deployment_files/R2015b/installers/glnxa64/MCR_R2015b_glnxa64_installer.zip && \
	unzip MCR_R2015b_glnxa64_installer.zip && \
	mkdir /opt/mcr && \
	./install -inputFile matlab.txt && \
	cd / && \
	rm -rf mcr-install

# Set environment
ENV JAVA_HOME /opt/jdk
ENV PATH ${PATH}:${JAVA_HOME}/bin




# PREVIOUS GENERATION BASED ON NEURALTALK
# fetch neuraltalk repo
WORKDIR /home
RUN git clone https://github.com/karpathy/neuraltalk
WORKDIR /home/neuraltalk

# fetch model checkpoint
WORKDIR /home/neuraltalk/cv
RUN wget http://cs.stanford.edu/people/karpathy/neuraltalk/coco_cnn_lstm_v2.zip
RUN unzip coco_cnn_lstm_v2.zip

# fetch coco data
WORKDIR /home/neuraltalk/data
RUN wget http://cs.stanford.edu/people/karpathy/deepimagesent/coco.zip
RUN unzip coco.zip

WORKDIR /home
RUN git clone https://github.com/mbartoli/theia
WORKDIR /home/neuraltalk/data
RUN wget http://shannon.cs.illinois.edu/DenotationGraph/data/flickr30k-images.tar
RUN wget http://shannon.cs.illinois.edu/DenotationGraph/data/flickr30k.tar.gz
RUN tar -xvf flickr30k-images.tar
RUN tar -xvf flickr30k.tar.gz





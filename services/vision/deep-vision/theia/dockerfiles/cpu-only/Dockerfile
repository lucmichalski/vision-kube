FROM tleyden5iwx/caffe-cpu-master
MAINTAINER Michael Bartoli <michael.bartoli@pomona.edu>

RUN apt-get update
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
	g++
RUN apt-get install -f
RUN pip install numpy scipy

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


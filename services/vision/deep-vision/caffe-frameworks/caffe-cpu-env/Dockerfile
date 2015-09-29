FROM ubuntu:14.04
MAINTAINER Siew Kar Fai <karfai0317@gmail.com>

ENV PYTHONPATH=/opt/caffe/python:$PYTHONPATH \
    PATH=/opt/caffe/.build_release/tools:/usr/local/MATLAB/R2014b/bin:/opt/conda/bin:$PATH

# Get dependencies
RUN apt-get update && apt-get install -y \
    bc \
    cmake \
    curl \
    gcc-4.6 \
    g++-4.6 \
    gcc-4.6-multilib \
    g++-4.6-multilib \
    gfortran \
    git \
    libprotobuf-dev \
    libleveldb-dev \
    libsnappy-dev \
    libopencv-dev \
    libboost-all-dev \
    libhdf5-serial-dev \
    liblmdb-dev \
    libjpeg62 \
    libfreeimage-dev \
    libatlas-base-dev \
    pkgconf \
    protobuf-compiler \
    python-dev \
    python-pip \
    unzip \
	wget && \
    apt-get clean

# Use gcc 4.6
RUN update-alternatives --install /usr/bin/cc cc /usr/bin/gcc-4.6 30 && \
    update-alternatives --install /usr/bin/c++ c++ /usr/bin/g++-4.6 30 && \
    update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-4.6 30 && \
    update-alternatives --install /usr/bin/g++ g++ /usr/bin/g++-4.6 30

# Install Glog and Gflags 
RUN cd /opt && \
    wget --quiet https://google-glog.googlecode.com/files/glog-0.3.3.tar.gz && \
    tar zxvf glog-0.3.3.tar.gz && \
    cd glog-0.3.3 && \
    ./configure && \
    make -j$(nproc) && \
    make install -j$(nproc) && \
    cd .. && \
    rm -rf glog-0.3.3.tar.gz && \ 
    ldconfig && \
    \
    cd /opt && \
    wget --quiet https://github.com/schuhschuh/gflags/archive/master.zip && \
    unzip master.zip && \
    cd gflags-master && \
    mkdir build && \
    cd build && \
    export CXXFLAGS="-fPIC" && \
    cmake .. && \
    make VERBOSE=1 && \
    make  -j$(nproc) && \
    make install -j$(nproc) && \
    cd ../.. && \
    rm master.zip

# Setup the Caffe, add caffe-ld-so.conf and create some symlinks
RUN cd /opt && \
    git clone https://github.com/BVLC/caffe.git && \
    cd caffe && \
    cp Makefile.config.example Makefile.config && \
    \
    echo "CPU_ONLY := 1" >> Makefile.config && \
    echo "CXX := /usr/bin/g++-4.6" >> Makefile.config && \
    echo "MATLAB_DIR := /usr/local/MATLAB/R2014b" >> Makefile.config && \
    echo "ANACONDA_HOME := /opt/conda" >> /opt/caffe/Makefile.config && \
    echo 'PYTHON_INCLUDE := $(ANACONDA_HOME)/include $(ANACONDA_HOME)/include/python2.7 $(ANACONDA_HOME)/lib/python2.7/site-packages/numpy/core/include' >> /opt/caffe/Makefile.config && \
    echo 'PYTHON_LIB := $(ANACONDA_HOME)/lib' >> /opt/caffe/Makefile.config && \
    echo 'INCLUDE_DIRS := $(PYTHON_INCLUDE) /usr/local/include' >> /opt/caffe/Makefile.config && \
    echo 'LIBRARY_DIRS := $(PYTHON_LIB) /usr/local/lib /usr/lib' >> /opt/caffe/Makefile.config && \
    sed -i 's/CXX :=/CXX ?=/' Makefile && \
    \
    cd python && \
    git clone https://github.com/sergeyk/selective_search_ijcv_with_python.git && \
    cd caffe && \
    sed -i 's/predictions = out\[self.outputs\[0\]\].squeeze(axis=(2, 3))/predictions = out\[self.outputs\[0\]\].squeeze()/' detector.py && \
    \
    cd /etc/ld.so.conf.d && \
    echo "/opt/caffe/.build_release/lib" > caffe-ld-so.conf && \
    \
    cd /usr/lib/x86_64-linux-gnu && \
    ln -s libhdf5.so.7 libhdf5.so.9 && \
    ln -s libhdf5_hl.so.7 libhdf5_hl.so.9 && \
    ldconfig

# Install python dependencies
RUN cd /opt && \
    wget --quiet https://repo.continuum.io/archive/Anaconda-2.2.0-Linux-x86_64.sh && \
    bash Anaconda-2.2.0-Linux-x86_64.sh -b -p /opt/conda && \
    rm Anaconda-2.2.0-Linux-x86_64.sh && \
    /opt/conda/bin/conda install --yes conda==3.10.1 && \
    cd /opt/caffe/python && \
    pip install -r requirements.txt
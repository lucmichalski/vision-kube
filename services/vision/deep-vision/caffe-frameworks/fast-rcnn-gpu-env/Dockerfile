FROM karfai/ubuntu-cuda
MAINTAINER Siew Kar Fai <karfai0317@gmail.com>

ADD tools/ /tmp

ENV PYTHONPATH=/opt/fast-rcnn/caffe-fast-rcnn/python:$PYTHONPATH \
    PATH=/opt/fast-rcnn/caffe-fast-rcnn/.build_release/tools:/usr/local/MATLAB/R2014b/bin:/opt/conda/bin:$PATH \
    LD_LIBRARY_PATH=/opt/conda/lib:/opt/libpng-1.5.15/lib:$LD_LIBRARY_PATH

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
    unzip && \
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
    git clone --recursive https://github.com/rbgirshick/fast-rcnn.git && \
    cd fast-rcnn/caffe-fast-rcnn && \
    cp Makefile.config.example Makefile.config && \
    \
    echo "CXX := /usr/bin/g++-4.6" >> Makefile.config && \
    echo "MATLAB_DIR := /usr/local/MATLAB/R2014b" >> Makefile.config && \
    echo "USE_CUDNN := 1" >> Makefile.config && \
    echo "ANACONDA_HOME := /opt/conda" >> /opt/fast-rcnn/caffe-fast-rcnn/Makefile.config && \
    echo 'PYTHON_INCLUDE := $(ANACONDA_HOME)/include $(ANACONDA_HOME)/include/python2.7 $(ANACONDA_HOME)/lib/python2.7/site-packages/numpy/core/include' >> /opt/fast-rcnn/caffe-fast-rcnn/Makefile.config && \
    echo 'PYTHON_LIB := $(ANACONDA_HOME)/lib' >> /opt/fast-rcnn/caffe-fast-rcnn/Makefile.config && \
    echo 'INCLUDE_DIRS := $(PYTHON_INCLUDE) /usr/local/include' >> /opt/fast-rcnn/caffe-fast-rcnn/Makefile.config && \
    echo 'LIBRARY_DIRS := $(PYTHON_LIB) /usr/local/lib /usr/lib' >> /opt/fast-rcnn/caffe-fast-rcnn/Makefile.config && \
    echo 'WITH_PYTHON_LAYER := 1' >> /opt/fast-rcnn/caffe-fast-rcnn/Makefile.config && \
    sed -i 's/CXX :=/CXX ?=/' Makefile && \
    \
    cd python && \
    git clone https://github.com/sergeyk/selective_search_ijcv_with_python.git && \
    cd caffe && \
    sed -i 's/predictions = out\[self.outputs\[0\]\].squeeze(axis=(2, 3))/predictions = out\[self.outputs\[0\]\].squeeze()/' detector.py && \
    sed -i 's/predictions = out\[self.outputs\[0\]\].squeeze(axis=(2,3))/predictions = out\[self.outputs\[0\]\].squeeze()/' detector.py && \
    \
    cd /etc/ld.so.conf.d && \
    echo "/opt/fast-rcnn/caffe-fast-rcnn/.build_release/lib" > caffe-ld-so.conf && \
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
    cd /opt/fast-rcnn/caffe-fast-rcnn/python && \
    conda install --yes cython && \
    conda install --yes opencv && \
    conda install --yes --channel https://conda.binstar.org/auto easydict && \
    pip install -r requirements.txt && \
    \
    rm /opt/conda/lib/libreadline* && \
    ldconfig && \
    \
    cd /tmp/libpng-1.5.15 && \
    ./configure --prefix=/opt/libpng-1.5.15 && \
    make check -j$(nproc) && \
    make install -j$(nproc) && \
    make check -j$(nproc) && \
    cd /opt && \
    rm -rf dump

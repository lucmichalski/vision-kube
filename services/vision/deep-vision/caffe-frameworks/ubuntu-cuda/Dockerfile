FROM ubuntu:14.04
MAINTAINER Siew Kar Fai <karfai0317@gmail.com>

# Cuda environment setup
ENV LD_LIBRARY_PATH=/usr/local/cuda-6.5/lib64:$LD_LIBRARY_PATH \
    PATH=/usr/local/cuda-6.5/bin:$PATH

# Install pre-requisite
RUN apt-get update && \
    apt-get install -q -y \
    wget \
    build-essential && \
    apt-get clean

# Install Cuda
RUN cd /opt && \
    wget --quiet http://developer.download.nvidia.com/compute/cuda/6_5/rel/installers/cuda_6.5.14_linux_64.run && \
    chmod +x cuda_6.5.14_linux_64.run && \
    mkdir nvidia_installers && \
    bash cuda_6.5.14_linux_64.run -extract=`pwd`/nvidia_installers && \
    cd nvidia_installers && \
    bash NVIDIA-Linux-x86_64-340.29.run -s -N --no-kernel-module && \
    bash cuda-linux64-rel-6.5.14-18749181.run -noprompt && \
    cd .. && \
    rm -rf cuda_6.5.14_linux_64.run nvidia_installers && \
    echo "/usr/local/cuda/lib64" > /etc/ld.so.conf.d/cuda.conf && \
    ldconfig

# Install cudnn
ADD cudnn-6.5-linux-x64-v2/libcudnn.so.6.5.48 /usr/local/cuda/lib64/
ADD cudnn-6.5-linux-x64-v2/cudnn.h /usr/local/cuda/include/

# Create soft links (symlinks)
RUN cd /usr/local/cuda/lib64 && \
    ln -s libcudnn.so.6.5.48 libcudnn.so.6.5 && \
    ln -s libcudnn.so.6.5 libcudnn.so && \
    ldconfig

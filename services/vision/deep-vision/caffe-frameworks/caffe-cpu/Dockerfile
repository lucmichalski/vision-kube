FROM karfai/caffe-cpu-env
MAINTAINER Siew Kar Fai <karfai0317@gmail.com>

ADD tools/gdown.pl /opt/

RUN cd /usr/local/ && \
    perl /opt/gdown.pl http://gdurl.com/jh7f/download MATLAB.zip && \
    unzip MATLAB.zip && \
    rm MATLAB.zip && \
    \
    cd /opt/caffe && \
    make all -j$(nproc) && \
    make pycaffe -j$(nproc) && \
    make matcaffe -j$(nproc) && \
    \
    cd /usr/local/ && \
    rm -rf MATLAB
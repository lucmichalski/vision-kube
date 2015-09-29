FROM karfai/fast-rcnn-cpu-env
MAINTAINER Siew Kar Fai <karfai0317@gmail.com>

ADD tools/gdown.pl /opt/

RUN cd /usr/local/ && \
    perl /opt/gdown.pl http://gdurl.com/jh7f/download MATLAB.zip && \
    unzip MATLAB.zip && \
    rm MATLAB.zip && \
    \
    cd /opt/fast-rcnn/lib && \
    make -j$(nproc) && \
    \
    cd /opt/fast-rcnn/caffe-fast-rcnn && \
    make all -j$(nproc) && \
    make pycaffe -j$(nproc) && \
    make matcaffe -j$(nproc) && \
    \
    cd /usr/local/ && \
    rm -rf MATLAB

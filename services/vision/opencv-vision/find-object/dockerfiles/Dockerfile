FROM ubuntu:14.04

# Install OpenCV 3.0
RUN apt-get -y update
RUN apt-get -y install python-pip git nano curl tmux htop mc wget libeigen3-dev
RUN apt-get install -y build-essential cmake libgtk2.0-dev pkg-config libavcodec-dev libavformat-dev libswscale-dev python-dev python-numpy libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libjasper-dev libdc1394-22-dev ocl-icd-opencl-dev libcanberra-gtk3-module python-pip
RUN pip install matplotlib

RUN git clone https://github.com/Itseez/opencv.git && git clone https://github.com/Itseez/opencv_contrib.git
RUN mkdir /opencv/build
WORKDIR /opencv/build
RUN pwd
RUN cmake -D CMAKE_BUILD_TYPE=RELEASE \
          -D CMAKE_INSTALL_PREFIX=/usr/local \
          -D WITH_TBB=ON \
          -D BUILD_PYTHON_SUPPORT=ON \
	  -D WITH_CUBLAS=ON \
          -D WITH_CUFFT=ON \
          -D WITH_TBB=ON \ 
	  -D WITH_OPENCL=ON \
	  -D BUILD_EXAMPLES=ON \
	  -D ENABLE_FAST_MATH=1 \ 
	  -D WITH_V4L=ON \
          -D WITH_QT4=ON \
          -D OPENCV_EXTRA_MODULES_PATH=/opencv_contrib/modules \
          ..
RUN make clean && make -j7
RUN make install

RUN ldconfig

WORKDIR /
RUN git clone https://github.com/introlab/find-object.git fo3
RUN apt-get install -y cmake libqt4-dev

WORKDIR /fo3/build
RUN cd /fo3/build && \
    cmake -DCMAKE_BUILD_TYPE=Release .. && \
    make -j 4 && \
    cd ../bin

ADD find-object.ini /fo/bin/find-object.ini

EXPOSE 5500
EXPOSE 4400
EXPOSE 6600
EXPOSE 7700
VOLUME /opt/dataset
VOLUME /fo3/bin

# Define default command.
CMD ["/fo3/bin/find-object","--objects","/opt/datasets","--images_not_saved","--config","/fo3/find-object.ini","--Camera/8port","5500","--General/port","4400","--console"]

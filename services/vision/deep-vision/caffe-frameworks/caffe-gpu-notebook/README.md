# From [Caffe Official](http://caffe.berkeleyvision.org/)
> "Caffe (Convolutional Architecture for Fast Feature Embedding) is a deep learning framework made with expression, speed, and modularity in mind. It is developed by the Berkeley Vision and Learning Center (BVLC) and by community contributors."

# What are the problems?
However, the installation of Caffe might piss you off, and following the Caffe official installation guide exactly is not sufficient, this is due to different machines have different hardware and environment, hence we need to deal with them differently as well.

# What can this repository do?
This repository will help you to take care of the dependencies carefully to make sure that you can work in less-worries environment, and what you need is just to focus on how to code your deep learning networks!

# Basic Requirement
The requirements on your machine

 - Cuda-6.5 along with compiled Cuda samples, consist of deviceQuery
 - Matlab R2014b with Image Processing Toolbox (other toolboxes might be required)

# Getting Started
In terminal (you might need root permission)

    $ docker pull karfai/caffe-gpu-notebook
    $ cd /path/to/samples/1_Utilities/deviceQuery
    $ ./deviceQuery
    $ DOCKER_NVIDIA_DEVICES="--device /dev/nvidia0:/dev/nvidia0 --device /dev/nvidiactl:/dev/nvidiactl --device /dev/nvidia-uvm:/dev/nvidia-uvm"
    $ MATLAB_MOUNT="-v /path/to/your/MATLAB:/usr/local/MATLAB"
    $ docker run -ti --rm -p 8888:8888 $DOCKER_NVIDIA_DEVICES $MATLAB_MOUNT karfai/caffe-gpu-notebook

Now, open a browser and connect to http://localhost:8888
Enjoy !!!

# Testing
The following notebook examples on Caffe are tested without any error:

 - [ImageNet Classification](http://nbviewer.ipython.org/github/BVLC/caffe/blob/master/examples/classification.ipynb)
 - [R-CNN detection](http://nbviewer.ipython.org/github/BVLC/caffe/blob/master/examples/detection.ipynb)

# Enquiries
If you have found any problem on this repository, please let me know. Thank you.

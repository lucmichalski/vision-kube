FROM alantrrs/docker-node-opencv3

# Install Octave & packages
RUN sudo apt-get -y update
RUN sudo apt-get install -y octave liboctave-dev
RUN sudo apt-get install -y octave-image

# Install vot-toolkit
RUN git clone https://github.com/vicoslab/vot-toolkit /vot-toolkit
# Use vot2014-final tag
WORKDIR /vot-toolkit
RUN git reset --hard b91af9ffb3a35a3bbe801018fbb7c146c2085d13

## Add custom vot_workspace
ADD . /
WORKDIR /vot_workspace

# Add /vot-toolkit to octave's path
# Compile MEX files and download the sequences
# Downloads TraX sources
RUN octave initialize.m

# Compile traxclient 
WORKDIR /vot-toolkit/trax/
RUN mkdir build
WORKDIR /vot-toolkit/trax/build/
RUN cmake -DBUILD_CLIENT=true ..
RUN make
RUN make install
RUN ldconfig

# Go back to default dir
WORKDIR /vot_workspace

# Define default command.
CMD ["bash"]

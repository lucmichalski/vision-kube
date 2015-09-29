FROM node:0.12.7
EXPOSE 4000
MAINTAINER Eugene Khvedchenya "ekhvedchenya@gmail.com"

# Clone the conf files into the docker container

RUN mkdir /cloudcv-bootstrap
ADD . /cloudcv-bootstrap

# Install app dependencies
RUN npm install -g node-gyp nan
RUN cd /cloudcv-bootstrap; npm install --unsafe-perm true

# Define working directory.
WORKDIR /cloudcv-bootstrap

# Define default command.
CMD ["npm", "start"]

FROM ubuntu:14.04
MAINTAINER Brian Morton "brian@mmm.hm"

RUN apt-get -y update && apt-get install -y curl git-core

# Install nodejs
RUN curl -sL https://deb.nodesource.com/setup | bash -
RUN apt-get install -y nodejs
RUN curl -sL https://www.npmjs.com/install.sh | sh
RUN npm install -g bower ember-cli

# Install nginx
RUN apt-get install -y software-properties-common
RUN \
  add-apt-repository -y ppa:nginx/stable && \
  apt-get update && \
  apt-get install -y nginx && \
  rm -rf /var/lib/apt/lists/* && \
  echo "\ndaemon off;" >> /etc/nginx/nginx.conf && \
  chown -R www-data:www-data /var/lib/nginx && \
  rm /etc/nginx/sites-enabled/default

ADD . /app
WORKDIR /app
RUN mv support/nginx.conf /etc/nginx/sites-enabled/vulcan-salute
RUN npm install
RUN bower install --allow-root
RUN ember build --environment production

EXPOSE 3000
CMD ["nginx"]

FROM ubuntu-debootstrap:14.04
RUN apt-get update -q \
  && DEBIAN_FRONTEND=noninteractive apt-get install -qy git wget nano 

ADD ./vmx-router /www
WORKDIR /www

CMD ["bash"]


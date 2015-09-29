#
# Gnu Compiler Collection: g++ Development tools. Which will run on ZCS
#

FROM       ubuntu

MAINTAINER Howard Young "https://github.com/threadx"

RUN apt-get update --fix-missing

RUN apt-get install -y g++
RUN apt-get install -y make
RUN apt-get install -y xutils-dev
RUN apt-get install -y libtool
RUN apt-get install -y automake

RUN apt-get install -y libzip-dev
RUN apt-get install -y libcurl4-openssl-dev
RUN apt-get install -y libxml2-dev

RUN apt-get install -y python
RUN apt-get install -y wget
RUN apt-get install -y pandoc
RUN apt-get install -y whois
RUN apt-get install -y traceroute
RUN apt-get install -y unzip
RUN apt-get install -y git
RUN apt-get install -y curl
RUN apt-get install -y strace

# vi just doesn't cut it anymore

RUN apt-get install -y vim

RUN apt-get update && apt-get install -y openssh-server

RUN mkdir /var/run/sshd
RUN echo 'root:zadara' | chpasswd

RUN sed -i 's/PermitRootLogin without-password/PermitRootLogin yes/' /etc/ssh/sshd_config
RUN echo 'UseDNS no' >> /etc/ssh/sshd_config

RUN sed 's@session\s*required\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd

ADD ./start.sh /start.sh

EXPOSE 22

## File systems

RUN apt-get install -y 	btrfs-tools \
						zfs-fuse


CMD /start.sh

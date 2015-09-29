FROM ubuntu:14.04

MAINTAINER Michael Bartoli <michael.bartoli@pomona.edu>

ENV JAVA_VERSION_MAJOR 8
ENV JAVA_VERSION_MINOR 60
ENV JAVA_VERSION_BUILD 27
ENV JAVA_PACKAGE       server-jre

RUN apt-get update
RUN apt-get -y install \
	curl \
	wget \
	unzip \
	xorg
	
# destinationFolder=/opt/mcr
# agreeToLicense=yes
# mode=silent

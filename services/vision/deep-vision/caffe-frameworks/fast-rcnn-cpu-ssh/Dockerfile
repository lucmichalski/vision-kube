FROM karfai/fast-rcnn-cpu
MAINTAINER Siew Kar Fai <karfai0317@gmail.com>

ENV NOTVISIBLE "in users profile"

RUN apt-get update && \
    apt-get install -y openssh-server && \
    mkdir /var/run/sshd && \
    echo 'root:caffe' | chpasswd && \
    sed -i 's/PermitRootLogin without-password/PermitRootLogin yes/' /etc/ssh/sshd_config && \
    sed 's@session\s*required\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd && \
    echo "export VISIBLE=now" >> /etc/profile

EXPOSE 22
CMD ["/usr/sbin/sshd", "-D"]

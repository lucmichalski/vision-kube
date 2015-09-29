FROM karfai/fast-rcnn-cpu
MAINTAINER Siew Kar Fai <karfai0317@gmail.com>

# Will add some caffe ipython notebooks example in the future release

ADD tools/ /opt/temp

RUN cd /opt/conda/bin && \
    conda install --yes seaborn && \
    conda create --yes -n python2 python=2 pip ipython pyzmq && \
    ipython profile create default --ipython-dir /root/.ipython && \
    \
    cd /opt/temp && \
    cp -r notebooks /root/ && \
    cp ipython_notebook_config.py /root/.ipython/profile_default/ && \
    cp matplotlib_nb_init.py /root/.ipython/profile_default/startup/ && \
    cd .. && \
    rm -rf temp && \
    \
    cd /opt/conda/envs/python2/bin && \
    python ipython kernelspec install-self

EXPOSE 8888
CMD /opt/conda/bin/ipython notebook

FROM rdhyee/scipyserver-ansible

MAINTAINER Raymond Yee  <raymond.yee@gmail.com>

# pip install -e git+https://github.com/commoncrawl/gzipstream.git#egg=gzipstream didn't work
RUN cd /tmp && git clone https://github.com/commoncrawl/gzipstream.git && cd gzipstream && python setup.py install && cd /tmp && rm -rf /tmp/gzipstream

RUN pip2.7 install -U boto==2.8.0 && \
  pip2.7 install warc && \
  pip2.7 install s3cmd && \
  apt-get clean -y

ADD CommonCrawl_basics.ipynb /notebooks/CommonCrawl_basics.ipynb
ADD basics.ipynb /notebooks/basics.ipynb
ADD factorials.py  /notebooks/factorials.py
ADD 2014_08_Crawl.ipynb /notebooks/2014_08_Crawl.ipynb

EXPOSE 8888
CMD /notebook.sh

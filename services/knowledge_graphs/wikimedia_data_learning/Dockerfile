FROM ipython/scipyserver

MAINTAINER Raymond Yee  <raymond.yee@gmail.com>

RUN apt-get -y install python-lxml libxml2-dev libxslt1-dev
        
#RUN pip2 install mwclient
#RUN pip2 install git+git://github.com/mwclient/mwclient@v0.7.1
#RUN pip2 install https://bitbucket.org/rdhyee/mwclient/get/wpp.tar.bz2
RUN pip2 install git+git://github.com/rdhyee/mwclient@wpp_integrate

RUN pip2 install responses && \
    pip2 install pytest && \
    pip2 install boltons && \
    pip2 install pywikibot && \
    pip2 install cssselect

VOLUME ["/notebooks", "/data"]

EXPOSE 8888

CMD /notebook.sh

FROM gliderlabs/alpine

RUN apk-install go git make docker ca-certificates

# Copy internal CA certificates
#COPY rootfs/usr/local/share/ca-certificates/ /usr/local/share/ca-certificates
#RUN update-ca-certificates

# Configure Go
ENV GOPATH /go
ENV PATH /go/bin:$PATH

# Install gb
RUN mkdir -p ${GOPATH}/{src,bin} ;\
    go get github.com/constabulary/gb/... ;\
    mv /go/bin/gb /bin

WORKDIR /go

CMD ["make"]

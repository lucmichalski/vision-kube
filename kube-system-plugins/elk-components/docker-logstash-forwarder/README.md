
# docker-logstash-forwarder
Lean (16MB) `logstash-forwarder` Docker image.

[![Docker Repository on Quay.io](https://quay.io/repository/pires/docker-logstash-forwarder/status "Docker Repository on Quay.io")](https://quay.io/repository/pires/docker-logstash-forwarder)

Here you will find a Dockerfile for assembling a `logstash-forwarder` container that communicates with `logstash` server over secure Lumberjack protocol. This could be the base for all your Docker images that need `logstash-forwarder`.

[runit](http://smarden.org/runit/) will make sure that your service runs on container start-up and supervises it. In case the service dies, `runit` brings it back to life. 

The image is available at [quay.io/pires](https://quay.io/repository/pires/docker-logstash-forwarder).

## Disclaimers

1. `runit` is installed from [`edge` packages](http://wiki.alpinelinux.org/wiki/Edge).
2. `logstash-forwarder` binary is available in `binaries` folder. Script to build said binary is also included.

## Pre-requisites

* Docker 1.6.2 or newer
* User account with permissions to run Docker client

## Run it

```
docker run --rm \
    --volume /path/to/config:/logstash-forwarder/config \
    --volume /path/to/certs:/logstash-forwarder/certs \
    quay.io/pires/docker-logstash-forwarder:0.4
```

## Build `logstash-forwarder`

The following command will clone [`logstash-forwarder` repo](git://github.com/elasticsearch/logstash-forwarder.git), build `logstash-forwarder` and copy the resulting binary to `binaries` folder.

**ATTENTION:** The images currently available have [support for multiple certificates in the SSL CA chain](https://github.com/elastic/logstash-forwarder/pull/261). This is not supported by the aforementioned build script.

```
./build_binaries.sh
```

Now, you just need to rebuild the image.

## SSL self-signed certificates

* `logstash` - needs `certificate` and `key`
* `logstash-forwarder` - needs `bundle ca certificate` (which must include `root ca certificate`)

```
openssl req -x509 -nodes -newkey rsa:2048 -keyout logstash-forwarder.key -out logstash-forwarder.crt
```

Be sure to set a `CN` that corresponds to a hostname resolvable by your hosts.

# Build Docker image

```bash
make
```

Or:

```bash
docker build --pull=true --no-cache -t alpine-golang:latest .
```

# Build using Docker container

```bash
docker run --rm -it -v "$PWD":/go -w /go alpine-golang:latest
```

This expects a Makefile in the current directory.

Or if you don't have a Makefile just add a command:

```bash
docker run --rm -it -v "$PWD":/go -w /go alpine-golang:latest gb build all
```

# Example Makefile

```
NAME:=$(shell basename `git rev-parse --show-toplevel`)
RELEASE:=$(shell git rev-parse --verify --short HEAD)

# Using internal registry
#USER=myuser
#PASS=mypass
#MAIL=noreply@example.com
#REGISTRY=docker-registry.example.com:8080

all: build

clean:
	rm -rf pkg bin

test: clean
	gb test

build: test
	gb build all

update:
	gb vendor update --all

docker-clean:
	docker rmi ${NAME} &>/dev/null || true

docker-build: docker-clean
       docker run --rm -it -v "$$PWD":/go -w /go alpine-golang:latest
       docker build --pull=true --no-cache -t ${NAME}:${RELEASE} .
       docker tag -f ${REGISTRY}/${NAME}:${RELEASE} ${NAME}:latest

# Using internal registry
#	docker run --rm -it -v "$$PWD":/go -w /go ${REGISTRY}/alpine-golang:latest
#	docker build --pull=true --no-cache -t ${REGISTRY}/${NAME}:${RELEASE} .
#	docker tag -f ${REGISTRY}/${NAME}:${RELEASE} ${REGISTRY}/${NAME}:latest

docker-push: docker-build

# Using internal registry
#	docker login -u ${USER} -p '${PASS}' -e ${MAIL} ${REGISTRY}
#	docker push ${REGISTRY}/${NAME}:${RELEASE}
#	docker push ${REGISTRY}/${NAME}:latest
```

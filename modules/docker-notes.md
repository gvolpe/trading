# Docker stuff

#### Build custom image

We need to install `curl` to the base image, used for health-checks.

```
$ docker build -t jdk17-curl .
```

#### Compare sizes

```
$ docker image ls --format "{{ .Size }}" openjdk:17-slim buster
400MB
$ docker image ls --format "{{ .Size }}" jdk17-curl:latest
422MB
```

#### Delete all trading images

```
$ docker images "trading-*" -a -q | xargs docker rmi -f
```

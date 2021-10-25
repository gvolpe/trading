FROM openjdk:17-slim-buster

RUN apt-get -y update
RUN apt-get -y upgrade
RUN apt-get -y install --no-install-recommends curl

# Clean up unnecessary stuff to reduce size
RUN apt-get -y clean
RUN rm -rf /var/lib/apt/lists/*

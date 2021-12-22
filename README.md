trading
=======

[![CI Elm](https://github.com/gvolpe/trading/workflows/Elm/badge.svg)](https://github.com/gvolpe/trading/actions)
[![CI Scala](https://github.com/gvolpe/trading/workflows/Scala/badge.svg)](https://github.com/gvolpe/trading/actions)
[![MergifyStatus](https://img.shields.io/endpoint.svg?url=https://gh.mergify.io/badges/gvolpe/trading&style=flat)](https://mergify.io)

Examples corresponding to the [Functional event-driven architecture: Powered by Scala 3](https://leanpub.com/feda) book.

## Table of contents

* [Web App](#web-app)
* [Requirements](#requirements)
* [Services](#services)
   * [Lib](#lib)
   * [Domain](#domain)
   * [Core](#core)
   * [Feed](#feed)
   * [Forecasts](#forecasts)
   * [Processor](#processor)
   * [Snapshots](#snapshots)
   * [Alerts](#alerts)
   * [WS Server](#ws-server)
   * [Tracing](#tracing)
   * [X Demo](#x-demo)
* [Monitoring](#monitoring)

## Web App

The web application allows users to subscribe/unsubscribe to/from symbol alerts such as `EURUSD`, which are emitted in real-time via Web Sockets.

![client](./imgs/webapp.png)

It is written in [Elm](https://elm-lang.org/) and can be built as follows.

```shell
$ cd web-app && nix-build
$ xdg-open result/index.html # or specify browser
```

There's also a `shell.nix` handy for local development.

```shell
$ cd web-app && nix-shell
$ elm make src/Main.elm --output=Main.js
$ xdg-open index.html # or specify browser
```

If Nix is not your jam, you can install Elm by following the [official instructions](https://guide.elm-lang.org/install/elm.html) and then compile as usual.

```shell
$ cd web-app
$ elm make src/Main.elm --output=Main.js
$ xdg-open index.html # or specify browser
```

## Requirements

The back-end application is structured as a mono-repo, and it requires both Apache Pulsar and Redis up and running. To make things easier, you can use the provided `docker-compose.yml` file.

```shell
$ docker-compose up -d pulsar redis
```

![pulsar](./imgs/pulsar.png)

To run the Kafka Demo (see more below), only Zookeeper and Kafka are needed.

```shell
$ docker-compose -f kafka.yml up
```

### Running application

If we don't specify any arguments, then all the containers will be started, including all our services (except `feed`), Prometheus, Grafana, and Pulsar Manager.

```shell
$ docker-compose up
Creating network "trading_trading-net" with the default driver
Creating trading_pulsar_1 ... done
Creating trading_redis_1  ... done
Creating trading_ws-server_1      ... done
Creating trading_pulsar-manager_1 ... done
Creating trading_alerts_1         ... done
Creating trading_processor_1      ... done
Creating trading_snapshots_1      ... done
Creating trading_tracing_1        ... done
Creating trading_prometheus_1     ... done
Creating trading_grafana_1        ... done
```

It is recommended to run the `feed` service directly from `sbt` whenever necessary, which publishes random data to the topics where other services are consuming messages from.

## Services

The back-end application consists of 8 modules, from which 5 are deployable applications, and 3 are just shared modules. There's also a demo module and a web application.

```
modules
├── alerts
├── core
├── domain
├── feed
├── forecasts
├── lib
├── processor
├── snapshots
├── tracing
├── ws-server
└── x-demo
```

![backend](./imgs/dev.png)

### Lib

Capability traits such as `Time`, `GenUUID`, and potential library abstractions such as `Consumer` and `Producer`, which abstract over different implementations such as Kafka and Pulsar.

### Domain

Commands, events, state, and all business-related data modeling.

### Core

Core functionality that needs to be shared across different modules such as snapshots, `AppTopic`, and `TradeEngine`.

### Feed

Generates random `TradeCommand`s and `ForecastCommand`s followed by publishing them to the corresponding topics. In the absence of real input data, this random feed puts the entire system to work.

### Forecasts

Registers new authors and forecasts, while calculating the author's reputation.

### Processor

The brain of the trading application. It consumes `TradeCommand`s, processes them to generate a `TradeState` and emitting `TradeEvent`s via the `trading-events` topic.

### Snapshots

It consumes `TradeEvent`s and recreates the `TradeState` that is persisted as a snapshot, running as a single instance in fail-over mode.

### Alerts

The alerts engine consumes `TradeEvent`s and emits `Alert` messages such as `Buy`, `StrongBuy` or `Sell` via the `trading-alerts` topic, according to the configured parameters.

### WS Server

It consumes `Alert` messages and sends them over Web Sockets whenever there's an active subscription for the alert.

### Tracing

A decentralized application that hooks up on multiple topics and creates traces via the Open Tracing protocol, using the Natchez library and Honeycomb.

![tracing](./imgs/tracing.png)

### X Demo

It contains all the standalone examples shown in the book as well. It also showcases both `KafkaDemo` and `MemDemo` programs that use the same `Consumer` and `Producer` abstractions defined in the `lib` module.

## Monitoring

JVM stats are provided for every service via Prometheus and Grafana.

![grafana](./imgs/grafana.png)

trading
=======

[![CI Status](https://github.com/gvolpe/trading/workflows/Build/badge.svg)](https://github.com/gvolpe/trading/actions)
[![MergifyStatus](https://img.shields.io/endpoint.svg?url=https://gh.mergify.io/badges/gvolpe/trading&style=flat)](https://mergify.io)

Examples corresponding to the [Event Driven Architecture meets Functional Programming in Scala](https://leanpub.com/eda-fp-scala) book.

### Requirements

The application is structured as a mono-repo, and it requires both Apache Pulsar and Redis up and running. To make things easier, you can use the provided `docker-compose.yml` file.

```shell
$ docker-compose up
```

To run the Kafka Demo (see more below), only Zookeeper and Kafka are needed.

```shell
$ docker-compose -f kafka.yml up
```

### Structure

```
.
├── alerts
├── core
├── domain
├── feed
├── lib
├── processor
├── snapshots
├── ws-client
├── ws-server
└── zoo-kafka-demo
```

#### Lib

Capability traits such as `Time` and potential library abstractions such as `Consumer` and `Producer`, which abstract over different implementations such as Kafka and Pulsar. Also generic typeclass instances such as `cats.Inject` based on Circe.

#### Domain

Commands, events, state, and all business-related data modeling.

#### Core

Core functionality that needs to be shared across different modules such as snapshots, `AppTopic`, and `EventSource`.

#### Feed

Generates random `TradeCommand`s such as `Create` or `Delete` and publishes them to the `trading-commands` topic.

#### Processor

The brain of the trading application. It consumes `TradeCommand`s, processes them to generate a `TradeState` and emitting `TradeEvent`s via the `trading-events` topic.

#### Snapshots

It consumes `TradeEvent`s and recreates the `TradeState` that is persisted as a snapshot every configurable amount of events.

#### Alerts

The alerts engine consumes `TradeEvent`s and emits `Alert` messages such as `Buy`, `StrongBuy` or `Sell` via the `trading-alerts` topic, according to the configured parameters.

#### WS Client

Allows you to follow symbols such as `EURUSD` and subscribe to alerts.

#### WS Server

It consumes `Alert` messages and sends them over Web Sockets whenever there's an active subscription for the alert.

#### Zoo Kafka Demo

It showcases a `KafkaDemo` program that uses the same `Consumer` and `Producer` abstractions defined in the `lib` module.

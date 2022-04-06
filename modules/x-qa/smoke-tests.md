# smokey

1. Run the essential applications via `docker-compose`.
2. Connect via WS and subscribe to a subset of symbols.
3. Generate and publish a fixed set of `TradeCommand`s.
4. Verify the WS client gets the expected alerts.

## Run smoke tests locally

Run the essential trading services from scratch.

```console
$ dc up pulsar redis processor alerts ws-server
```

Run the smoke tests.

```console
$ sbt smokey/test
```

### Helpers

A few commands that might help clearing the internal state if something goes wrong (otherwise, tearing all the containers down and restarting them up again should do).

Delete both the `trading-alerts` and the `trading-switch-events` topics.

```console
$ docker-compose exec pulsar bin/pulsar-admin topics delete persistent://public/default/trading-alerts
$ docker-compose exec pulsar bin/pulsar-admin topics delete persistent://public/default/trading-switch-events
```

For these commands to succeed, there should be no active producers / consumers for these topics.

Clear Redis state.

```console
docker-compose exec redis redis-cli FLUSHALL
```

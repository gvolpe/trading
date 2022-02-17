package demo.tracer

import trading.trace.Config.HoneycombApiKey

import dev.profunktor.pulsar.{ Config as PulsarConfig, Subscription, Topic }

val sub =
  Subscription.Builder
    .withName("tracer-demo")
    .withType(Subscription.Type.Failover)
    .build

val pulsarCfg =
  PulsarConfig.Builder
    .withTenant("public")
    .withNameSpace("default")
    .withURL("pulsar://localhost:6650")
    .build

val nameTopic: Topic.Single =
  Topic.Builder
    .withName(Topic.Name("user-names"))
    .withConfig(pulsarCfg)
    .withType(Topic.Type.NonPersistent)
    .build

val userTopic: Topic.Single =
  Topic.Builder
    .withName(Topic.Name("new-users"))
    .withConfig(pulsarCfg)
    .withType(Topic.Type.NonPersistent)
    .build

val apiKey = HoneycombApiKey(System.getenv("HONEYCOMB_API_KEY"))

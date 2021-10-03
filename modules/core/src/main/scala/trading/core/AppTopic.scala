package trading.core

import dev.profunktor.pulsar.{ Config, Topic }

sealed abstract class AppTopic {
  def name: String
  def make(cfg: Config): Topic.Single
}

object AppTopic:
  case object Alerts extends AppTopic {
    val name: String                    = "trading-alerts"
    def make(cfg: Config): Topic.Single = mkNonPersistent(cfg, name)
  }

  case object TradingCommands extends AppTopic {
    val name: String                    = "trading-commands"
    def make(cfg: Config): Topic.Single = mkNonPersistent(cfg, name)
  }

  case object TradingEvents extends AppTopic {
    val name: String                    = "trading-events"
    def make(cfg: Config): Topic.Single = mkPersistent(cfg, name)
  }

  private def mkNonPersistent(cfg: Config, name: String): Topic.Single =
    Topic.Builder
      .withName(Topic.Name(name))
      .withConfig(cfg)
      .withType(Topic.Type.NonPersistent)
      .build

  private def mkPersistent(cfg: Config, name: String): Topic.Single =
    Topic.Builder
      .withName(Topic.Name(name))
      .withConfig(cfg)
      .withType(Topic.Type.Persistent)
      .build

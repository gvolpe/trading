package trading.lib

trait Acker[F[_], A]:
  def ack(id: Consumer.MsgId): F[Unit]
  def ack(ids: Set[Consumer.MsgId]): F[Unit]
  def nack(id: Consumer.MsgId): F[Unit]

object Acker:
  def from[F[_], A](consumer: Consumer[F, A]): Acker[F, A] = new:
    def ack(id: Consumer.MsgId): F[Unit]       = consumer.ack(id)
    def ack(ids: Set[Consumer.MsgId]): F[Unit] = consumer.ack(ids)
    def nack(id: Consumer.MsgId): F[Unit]      = consumer.nack(id)

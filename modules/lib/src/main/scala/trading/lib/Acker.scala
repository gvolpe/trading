package trading.lib

trait Acker[F[_], A]:
  def ack(id: Consumer.MsgId): F[Unit]
  def ack(ids: Set[Consumer.MsgId]): F[Unit]
  def ack(id: Consumer.MsgId, tx: Txn): F[Unit]
  def nack(id: Consumer.MsgId): F[Unit]

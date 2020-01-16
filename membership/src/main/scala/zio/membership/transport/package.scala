package zio.membership

import zio._
import zio.stream._
import zio.nio.SocketAddress

package object transport extends Transport.Service[Transport] {

  override def send(to: SocketAddress, data: Chunk[Byte]): ZIO[Transport, TransportError, Unit] =
    ZIO.accessM(_.transport.send(to, data))

  override def bind(addr: SocketAddress): ZStream[Transport, TransportError, Chunk[Byte]] =
    ZStream.unwrap {
      ZIO.environment.map(_.transport.bind(addr))
    }

}

package zio.membership.transport

import zio._
import zio.nio._
import zio.nio.channels._
import zio.stream._
import zio.duration._
import zio.clock.Clock
import zio.membership.{ BindFailed, ExceptionThrown, RequestTimeout }
import java.math.BigInteger
import zio.macros.delegate._
import zio.membership.RequestTimeout

object tcp {

  def withTcpTransport(
    maxConnections: Int,
    connectionTimeout: Duration,
    sendTimeout: Duration
  ): EnrichWithM[Clock, Nothing, Transport] =
    enrichWithM[Transport](tcpTransport(maxConnections, connectionTimeout, sendTimeout))

  def tcpTransport(
    maxConnections: Int,
    connectionTimeout: Duration,
    sendTimeout: Duration
  ): ZIO[Clock, Nothing, Transport] =
    ZIO.environment[Clock].map { env =>
      new Transport {
        val transport = new Transport.Service[Any] {
          // TODO: cache connections
          override def send(to: SocketAddress, data: Chunk[Byte]) =
            AsynchronousSocketChannel()
              .use { client =>
                val size = data.size
                for {
                  _ <- client.connect(to)
                  _ <- client.write(Chunk((size >>> 24).toByte, (size >>> 16).toByte, (size >>> 8).toByte, size.toByte))
                  _ <- client.write(data)
                } yield ()
              }
              .mapError(ExceptionThrown(_))
              .timeoutFail(RequestTimeout(sendTimeout))(sendTimeout)
              .provide(env)

          override def bind(addr: SocketAddress) =
            ZStream
              .unwrapManaged {
                AsynchronousServerSocketChannel()
                  .flatMap(s => s.bind(addr).toManaged_.as(s))
                  .mapError(BindFailed(addr, _))
                  .withEarlyRelease
                  .map {
                    case (close, server) =>
                      val messages = ZStream
                        .repeatEffect(server.accept.preallocate)
                        .flatMapPar[Clock, Throwable, Chunk[Byte]](maxConnections) { connection =>
                          ZStream.unwrapManaged {
                            connection.map { channel =>
                              ZStream
                                .repeatEffect(
                                  for {
                                    length <- channel
                                               .read(4)
                                               .flatMap(c => ZIO.effect(new BigInteger(c.toArray).intValue()))
                                    data <- channel.read(length * 8)
                                  } yield data
                                )
                                .timeout(connectionTimeout)
                                .catchAllCause(_ => ZStream.empty)
                            }
                          }
                        }
                        .mapError(ExceptionThrown)
                      // hack to properly handle interrupts
                      messages.merge(ZStream.never.ensuring(close))
                  }
              }
              .provide(env)
        }
      }
    }
}

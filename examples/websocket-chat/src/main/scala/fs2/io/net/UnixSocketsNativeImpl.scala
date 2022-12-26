package fs2.io.net

import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.io.net.unixsocket.{UnixSocketAddress, UnixSockets}

import java.net.UnixDomainSocketAddress
import java.nio.channels.AsynchronousSocketChannel

object UnixSocketsNativeImpl extends SocketCompanionPlatform {

  implicit val sockets: UnixSockets[IO] = new UnixSockets[IO] {
    override def client(address: UnixSocketAddress): Resource[IO, Socket[IO]] = {
      Resource.make(IO {
        val channel = AsynchronousSocketChannel.open()
        channel.connect(UnixDomainSocketAddress.of(address.path)).get
        channel
      }) { s =>
        IO {
          s.close()
        }
      }
    }.flatMap(forAsync[IO](_))

    override def server(address: UnixSocketAddress, deleteIfExists: Boolean, deleteOnClose: Boolean): fs2.Stream[IO, Socket[IO]] =
      fs2.Stream.resource({
        Resource.make(IO {
          val channel = AsynchronousSocketChannel.open()
          channel.bind(UnixDomainSocketAddress.of(address.path))
          channel
        }) { s =>
          IO {
            s.close()
          }
        }
      }.flatMap(forAsync[IO](_)))
  }
}

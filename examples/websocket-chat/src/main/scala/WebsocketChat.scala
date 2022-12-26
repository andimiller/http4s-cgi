import cats.effect.kernel.Resource
import cats.effect.{IO, Ref, Sync}
import net.andmiller.http4s.cgi.WebsocketdApp
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes, Request, Uri}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import fs2._
import fs2.io.file.{Path => FS2Path}
import fs2.io.file.Files
import fs2.io.file.Watcher.Event
import fs2.io.net.Socket
import fs2.io.net.UnixSocketsNativeImpl.sockets
import fs2.io.net.unixsocket.{UnixSocketAddress, UnixSockets}

object Name {
  def unapply(req: Request[_]): Option[String] =
    req.uri.query.params.keys
      .find(_.toLowerCase == "name")
      .flatMap(req.uri.query.params.get)
}

object ChatSocket {
  def bind[F[_]: UnixSockets: Sync](name: String): Resource[F, Socket[F]]     =
    UnixSockets[F].server(UnixSocketAddress(s"./chat/$name")).compile.resource.lastOrError
  def connect[F[_]: UnixSockets: Sync](path: FS2Path): Resource[F, Socket[F]] = UnixSockets[F].client(UnixSocketAddress(path.toString))
}

object Discovery {
  sealed trait DiscoveryEvent
  case class Add(path: FS2Path)    extends DiscoveryEvent
  case class Remove(path: FS2Path) extends DiscoveryEvent
  def apply[F[_]: UnixSockets: Files: Sync](name: String): Stream[F, DiscoveryEvent] = {
    Files[F].walk(fs2.io.file.Path("./chat/")).collect {
      case path if !path.endsWith(name) => Add(path)
    } ++ Files[F].watch(fs2.io.file.Path("./chat/")).collect {
      case Event.Created(path, _) if !path.endsWith(name) => Add(path)
      case Event.Deleted(path, _)                         => Remove(path)
    }
  }
}

/** This is an example websocket server which lets people join and chat to each other via a socket
  */
object WebsocketChat extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws =>
    HttpRoutes
      .of[IO] { case Name(name) =>
        Ref.of[IO, Vector[(Path, Socket[IO])]](Vector.empty).flatMap { peers =>
          val inbound = fs2.Stream
            .resource(ChatSocket.bind[IO](name))
            .flatMap(_.reads.through(fs2.text.utf8.decode))
            .through(fs2.text.lines)
            .map(s => WebSocketFrame.Text(s))
          ws.build { _ =>
            inbound
          }
        }
      }
      .orNotFound
}

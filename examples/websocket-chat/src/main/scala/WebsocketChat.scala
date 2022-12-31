import cats.effect.kernel.Resource
import cats.effect._
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits._
import fs2.concurrent.Topic
import fs2.io.net.Socket
import fs2.io.net.unixsocket.{UnixSocketAddress, UnixSockets}
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.syntax.EncoderOps
import net.andmiller.http4s.cgi.WebsocketdApp
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes, Request, Uri}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame

import java.time.Instant
import scala.concurrent.duration.{DurationDouble, DurationInt}

object Name {
  def unapply[F[_]](req: Request[F]): Option[String] =
    req.uri.query.params.keys
      .find(_.toLowerCase == "name")
      .flatMap(req.uri.query.params.get)
      .filter(_.forall(_.isLetter))
}

case class ChatLog(time: Instant, name: String, message: String)
object ChatLog {
  implicit val codec: Codec[ChatLog] = deriveCodec
}

object ReadSocket extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    UnixSockets[IO].server(UnixSocketAddress("/tmp/test.sock")).compile.resource.lastOrError.use { sock =>
      sock.reads.through(fs2.text.utf8.decode[IO]).through(fs2.text.lines[IO]).evalMap(IO.println(_)).compile.drain.as(ExitCode.Success)
    }
}

/** This is an example websocket server which lets people join and chat to each other via redis
  */
/*
object WebsocketChat extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws => {
    HttpRoutes
      .of[IO] { case Name(name) =>
        UnixSockets[IO]
          .server(UnixSocketAddress(s"/tmp/chat.$name.sock"), deleteIfExists = true, deleteOnClose = true)
          .compile
          .resource
          .lastOrError
          .use { case inSock =>
            ws.build { input =>
              inSock.reads.through(fs2.text.utf8.decode[IO]).through(fs2.text.lines[IO]).map(WebSocketFrame.Text(_))
            }
          }

      }
      .orNotFound
  }
}


 */

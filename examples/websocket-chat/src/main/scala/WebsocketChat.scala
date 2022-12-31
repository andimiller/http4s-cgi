import cats.effect._
import fs2.io.file.{Files, Path => FPath}
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

/** This is an example websocket server which lets people join and chat to each other via redis
  */
object WebsocketChat extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws => {
    HttpRoutes
      .of[IO] { case Name(name) =>
        ws.build { input =>
          val in  = input
            .collect { case WebSocketFrame.Text(text, _) => text }
            .evalMap { s => IO { ChatLog(Instant.now(), name, s).asJson.noSpaces } }
            .evalMap { msg =>
              Files[IO]
                .list(FPath("/tmp"))
                .map(_.toString)
                .filter(_.endsWith("chat.sock"))
                .evalTap { handle =>
                  UnixSockets[IO].client(UnixSocketAddress(handle)).use { sock =>
                    fs2.Stream.emit(msg).through(fs2.text.utf8.encode[IO]).through(sock.writes).compile.drain
                  }
                }
                .compile
                .drain
            }
          val out = UnixSockets[IO]
            .server(UnixSocketAddress(s"/tmp/$name.chat.sock"), deleteIfExists = true, deleteOnClose = true)
            .flatMap { sock =>
              sock.reads
                .through(fs2.text.utf8.decode[IO])
                .through(fs2.text.lines[IO])
                .evalMap { s => IO.fromEither(io.circe.parser.parse(s).flatMap(_.as[ChatLog])) }
                .map { chatLog =>
                  WebSocketFrame.Text(
                    s"[${chatLog.time}] <${chatLog.name}> ${chatLog.message}"
                  )
                }
            }
            .onFinalize(Files[IO].delete(FPath(s"/tmp/$name.chat.sock")))
          out.concurrently(in)
        }
      }
      .orNotFound
  }
}

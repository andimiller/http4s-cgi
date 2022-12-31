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

/** This is an example websocket server which lets people join and chat to each other via unix sockets
  */
object WebsocketChat extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws => {
    HttpRoutes
      .of[IO] { case Name(name) => // each user should provide a name as a query parameter
        ws.build { input =>
          val in = input
            .collect { case WebSocketFrame.Text(text, _) => text }
            .evalMap { s =>
              IO { ChatLog(Instant.now(), name, s).asJson.noSpaces }
            } // inputs are put into a ChatLog object with a timestamp and the user who said it
            .evalMap { msg =>
              // Loop over all the sockets and message the ones we find
              Files[IO]
                .list(FPath("./chatsockets/"))
                .map(_.toString)
                .filter(_.endsWith("chat.sock"))
                .evalTap { handle =>
                  UnixSockets[IO]
                    .client(UnixSocketAddress(handle))
                    .use { sock =>
                      fs2.Stream.emit(msg).through(fs2.text.utf8.encode[IO]).through(sock.writes).compile.drain
                    }
                    .attempt // sometimes there's a stale socket and we'd like to just pretend everything's okay in this demo
                    .void
                }
                .compile
                .drain
            }

          // our outputs are purely what's sent to our socket
          val out = UnixSockets[IO]
            .server(UnixSocketAddress(s"./chatsockets/$name.chat.sock"), deleteIfExists = true, deleteOnClose = true)
            .flatMap { sock =>               // each new connection is another process sending us a message
              sock.reads
                .through(fs2.text.utf8.decode[IO])
                .through(fs2.text.lines[IO]) // they should be line delimited
                .evalMap { s =>
                  IO.fromEither(io.circe.parser.parse(s).flatMap(_.as[ChatLog]))
                }                            // they should be ChatLogs serialized as JSON
                .map { chatLog =>
                  WebSocketFrame.Text(
                    s"[${chatLog.time}] <${chatLog.name}> ${chatLog.message}" // present it nicely for the user
                  )
                }
            }
            .onFinalize(Files[IO].delete(FPath(s"./chatsockets/$name.chat.sock"))) // delete our socket if we left it by accident
          out.concurrently(in)
        }
      }
      .orNotFound
  }
}

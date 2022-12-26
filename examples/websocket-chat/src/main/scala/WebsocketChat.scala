import cats.effect.kernel.Resource
import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits._
import fs2.concurrent.Topic
import io.chrisdavenport.rediculous.{RedisCommands, RedisConnection, RedisPubSub}
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

/** This is an example websocket server which lets people join and chat to each other via a sqlite db
  */
object WebsocketChat extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws =>
    HttpRoutes
      .of[IO] { case Name(name) =>
        Topic[IO, ChatLog].flatMap { topic =>
          IO.println(ChatLog(Instant.now(), "test name", "message").asJson) *> RedisConnection
            .direct[IO]
            .build
            .flatMap(RedisPubSub.fromConnection(_))
            .use { pubsub =>
              pubsub.subscribe(
                "chat",
                { m =>
                  IO.fromEither(io.circe.parser.parse(m.message))
                    .flatMap(j => IO.fromEither(j.as[ChatLog]))
                    .flatTap(IO.println(_))
                    .flatMap(topic.publish1)
                    .void
                }
              ) *> ws.build { _ =>
                topic
                  .subscribe(1000)
                  .map(_.asJson.noSpaces)
                  .map(s => WebSocketFrame.Text(s))
              }
            }
        }

      }
      .orNotFound
}

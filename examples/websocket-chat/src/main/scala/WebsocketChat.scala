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

/** This is an example websocket server which lets people join and chat to each other via redis
  */
object WebsocketChat extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws =>
    HttpRoutes
      .of[IO] { case Name(name) =>
        Topic[IO, String].flatMap { topic =>
          ws.build(
            fs2.Stream
              .resource(
                RedisConnection.direct[IO].build.flatMap(RedisPubSub.fromConnection(_))
              )
              .flatMap { pubsub =>
                (fs2.Stream.eval(
                  pubsub
                    .subscribe(
                      "chat",
                      { m =>
                        topic.publish1(m.message).void
                      }
                    )
                    .as("Connected")
                ) ++ topic.subscribe(1000)).map(s => WebSocketFrame.Text(s))
              },
            input =>
              fs2.Stream
                .resource(
                  RedisConnection.direct[IO].build.flatMap(RedisPubSub.fromConnection(_))
                )
                .flatMap { pubsub =>
                  input
                    .collect { case WebSocketFrame.Text(s, _) => s }
                    .evalTap(s => pubsub.publish("chat", ChatLog(Instant.now(), name, s).asJson.noSpaces))
                    .void
                }
          )
        }

      }
      .orNotFound
}

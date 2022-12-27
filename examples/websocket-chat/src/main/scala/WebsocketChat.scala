import cats.effect.kernel.Resource
import cats.effect._
import cats.effect.std.{Dispatcher, Queue}
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

/** This is an example websocket server which lets people join and chat to each other via redis
  */
object WebsocketChat extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws =>
    HttpRoutes
      .of[IO] { case Name(name) =>
        val pubsub = fs2.Stream
          .resource(
            RedisConnection.direct[IO].build.flatMap(RedisPubSub.fromConnection(_))
          )
        ws.build { input =>
          fs2.Stream.eval(Queue.unbounded[IO, String]).flatMap { topic =>
            (pubsub, pubsub).tupled
              .flatMap { case (pub, sub) =>
                val out: fs2.Stream[IO, WebSocketFrame.Text] = (fs2.Stream.eval(
                  sub
                    .subscribe(
                      "chat",
                      m => {
                        topic.offer(m.message).void
                      }
                    )
                    .as("Connected")
                ) ++ fs2.Stream.awakeEvery[IO](1.seconds).evalMap(_ => topic.tryTake).flattenOption).map(s => WebSocketFrame.Text(s))
                val in                                       = input
                  .collect { case WebSocketFrame.Text(s, _) => s }
                  .map(s => ChatLog(Instant.now(), name, s).asJson.noSpaces)
                  .evalTap(s => pub.publish("chat", s))
                out.concurrently(in).concurrently(fs2.Stream.eval(sub.runMessages *> IO.cede *> IO.sleep(0.1.seconds)).repeat)
              }
          }
        }
      }
      .orNotFound
}

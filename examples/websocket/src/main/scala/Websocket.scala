import cats.effect.IO
import net.andmiller.http4s.cgi.WebsocketdApp
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import fs2._

import scala.concurrent.duration.DurationInt

/** This is an example websocket server which sends ticks every 1s, and also reverses any user input and echoes it back
  */
object Websocket extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws =>
    HttpRoutes
      .of[IO] { case GET -> Root / "ws" =>
        fs2.concurrent.Topic[IO, String].flatMap { replyTopic =>
          val send: Stream[IO, WebSocketFrame]        =
            Stream
              .awakeEvery[IO](1.second)
              .evalMap(time => IO(WebSocketFrame.Text(s"ok $time")))
              .mergeHaltBoth(replyTopic.subscribe(100).map(WebSocketFrame.Text(_)))
          val receive: Pipe[IO, WebSocketFrame, Unit] =
            in =>
              in.evalMap {
                case text: WebSocketFrame.Text => replyTopic.publish1(text.str.reverse).void
                case _                         => IO.unit
              }
          ws.build(send, receive)
        }
      }
      .orNotFound
}

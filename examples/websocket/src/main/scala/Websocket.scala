import cats.effect.IO
import net.andmiller.http4s.cgi.WebsocketdApp
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import fs2._

import scala.concurrent.duration.DurationInt

/** This is an example websocket server which reverses any user input and echoes it back reversed
  */
object Websocket extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws =>
    HttpRoutes
      .of[IO] { case _ =>
        ws.build { in =>
          in.collect { case text: WebSocketFrame.Text =>
            WebSocketFrame.Text(text.str.reverse)
          }
        }
      }
      .orNotFound
}

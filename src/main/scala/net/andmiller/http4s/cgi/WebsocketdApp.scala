package net.andmiller.http4s.cgi

import cats.effect.{ExitCode, IO, IOApp}
import epollcat.EpollApp
import org.http4s.HttpApp
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocketd.WebsocketdServerBuilder

abstract class WebsocketdApp extends EpollApp {
  def create: WebSocketBuilder[IO] => HttpApp[IO]

  override def run(args: List[String]): IO[ExitCode] =
    WebsocketdServerBuilder.run[IO](create).as(ExitCode.Success)
}

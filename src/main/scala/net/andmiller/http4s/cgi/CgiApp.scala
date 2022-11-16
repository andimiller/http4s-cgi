package net.andmiller.http4s.cgi

import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.HttpApp

abstract class CgiApp extends IOApp {
  def routes: HttpApp[IO]

  override def run(args: List[String]): IO[ExitCode] =
    CgiServerBuilder.run[IO](routes).as(ExitCode.Success)
}

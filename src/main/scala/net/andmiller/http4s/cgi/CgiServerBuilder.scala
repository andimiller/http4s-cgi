package net.andmiller.http4s.cgi

import cats.data.OptionT
import cats.effect.{Async, LiftIO, Sync}
import cats.effect.std.Console
import cats.implicits._
import org.http4s.headers.`Content-Type`
import org.http4s.{Entity, Header, Headers, HttpApp, Method, Request, Uri}
import org.http4s.implicits._

import scala.jdk.CollectionConverters.MapHasAsScala

object CgiServerBuilder {

  implicit class EnvSyntax(map: Map[String, String]) {
    def getOrRaise[F[_]: Sync](key: String): F[String] =
      map.get(key).map(_.pure[F]).getOrElse(Sync[F].raiseError(new EnvironmentVariableNotFound(key)))
    def getHeaders: Headers                            = Headers(map.toList.filter(_._1.startsWith("HTTP_")).map { case (header, value) =>
      Header.Raw(header.stripPrefix("HTTP_").replace('_', '-').ci, value)
    })
  }

  def run[F[_]: Async: Console: LiftIO](routes: HttpApp[F]): F[Unit] = for {
    env          <- Sync[F].delay { System.getenv().asScala.toMap }
    method       <- env.getOrRaise[F]("REQUEST_METHOD").flatMap(s => Sync[F].fromEither(Method.fromString(s)))
    queryString   = env.get("QUERY_STRING").map(s => "?" + s)
    uri          <- env.getOrRaise[F]("SCRIPT_NAME").flatMap(s => Sync[F].fromEither(Uri.fromString(s + queryString.getOrElse(""))))
    contentType  <-
      OptionT.fromOption[F](env.get("CONTENT_TYPE").filter(_.nonEmpty)).semiflatMap(s => Sync[F].fromEither(`Content-Type`.parse(s))).value
    contentLength = env.get("CONTENT_LENGTH").filter(_.nonEmpty).flatMap(_.toLongOption)
    headers       = env.getHeaders ++ Headers(contentType.toList)
    request       = Request[F](
                      method = method,
                      uri = uri,
                      headers = headers,
                      entity = Entity(fs2.io.stdin[F](1024), contentLength)
                    )
    response     <- routes(request)
    _            <- response.headers.headers.appended(Header.Raw("Status".ci, response.status.renderString)).traverse { h =>
                      Console[F].println(h.toString())
                    }
    _            <- Console[F].println("")
    _            <- response.body.through(fs2.io.stdout[F]).compile.drain
  } yield ()

}

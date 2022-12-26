package org.http4s.websocketd

import cats.data.OptionT
import cats.effect.{Async, Concurrent, GenConcurrent, Sync, Unique}
import cats.effect.std.Console
import cats.implicits._
import net.andmiller.http4s.cgi.CgiServerBuilder
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.{WebSocketCombinedPipe, WebSocketFrame, WebSocketSeparatePipe}

import scala.jdk.CollectionConverters.MapHasAsScala

object WebsocketdServerBuilder {
  import CgiServerBuilder.EnvSyntax

  def escapeNewlines(s: String): String   = s.replace("\n", "\\n")
  def unescapeNewlines(s: String): String = s.replace("\\n", "\n")

  def run[F[_]: Async: Console](create: WebSocketBuilder[F] => HttpApp[F]): F[Unit] = for {
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
    builder      <- WebSocketBuilder[F]
    response     <- create(builder).apply(request)
    // check if it's websockets
    _            <- response.attributes.lookup(builder.webSocketKey) match {
                      case Some(ctx) =>
                        ctx.webSocket match {
                          case WebSocketSeparatePipe(send, receive, onClose) =>
                            // input
                            val in: fs2.Stream[F, Unit]  =
                              fs2.io
                                .stdinUtf8[F](1024)
                                .map(unescapeNewlines)
                                .through(fs2.text.lines[F])
                                .map(WebSocketFrame.Text(_))
                                .through(receive)
                            // output
                            val out: fs2.Stream[F, Unit] = send
                              .map {
                                case text: WebSocketFrame.Text => Some(text.str)
                                case _                         => None
                              }
                              .flattenOption
                              .map(escapeNewlines)
                              .evalMap(s => Console[F].println(s))
                            Console[F].println("separate mode") *> in.concurrently(out).onFinalize(onClose).compile.drain
                          case WebSocketCombinedPipe(receiveSend, onClose)   =>
                            fs2.io
                              .stdinUtf8[F](1024)
                              .map(unescapeNewlines)
                              .through(fs2.text.lines[F])
                              .evalTap(l => Console[F].println(s"got input line: $l"))
                              .map(WebSocketFrame.Text(_))
                              .through(receiveSend)
                              .evalTap(f => Console[F].println(s"got frame: $f"))
                              .collect { case text: WebSocketFrame.Text =>
                                text.str
                              }
                              .evalTap(f => Console[F].println(s"outputting frame: $f"))
                              .map(escapeNewlines)
                              .evalMap(s => Console[F].println(s))
                              .onFinalize(onClose)
                              .compile
                              .drain

                        }
                      case None      =>
                        response.headers.headers.appended(Header.Raw("Status".ci, response.status.renderString)).traverse { h =>
                          Console[F].println(h.toString())
                        } *> Console[F].println("") *> response.body.through(fs2.io.stdout[F]).compile.drain
                    }
  } yield ()

}

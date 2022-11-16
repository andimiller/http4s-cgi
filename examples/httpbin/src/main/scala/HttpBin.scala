import cats.effect.IO
import io.circe.Json
import io.circe.syntax.EncoderOps
import net.andmiller.http4s.cgi.CgiApp
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.circe._

object HttpBin extends CgiApp {

  override val routes: HttpApp[IO] = HttpRoutes
    .of[IO] { case req =>
      Ok(
        Json.obj(
          "message" -> Json.fromString("hello world"),
          "method"  -> Json.fromString(req.method.toString()),
          "uri"     -> Json.fromString(req.uri.renderString),
          "params"  -> req.params.asJson,
          "headers" -> Json.arr(req.headers.headers.map { h =>
            Json.obj("name" -> Json.fromString(h.name.toString), "value" -> Json.fromString(h.value))
          }: _*)
        )
      )
    }
    .orNotFound
}

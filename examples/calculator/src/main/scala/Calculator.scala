import cats.effect.IO
import io.circe.Json
import io.circe.syntax.EncoderOps
import net.andmiller.http4s.cgi.CgiApp
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.circe._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import io.circe.generic.semiauto._

case class Req(left: Long, right: Long, operator: String)
object Req {
  implicit val codec = deriveCodec[Req]
}

object Calculator extends CgiApp {

  override val routes: HttpApp[IO] = HttpRoutes
    .of[IO] { case req @ POST -> Root / "calculator" =>
      req.as[Req].flatMap {
        case Req(l, r, "+") => Ok((l + r).asJson)
        case Req(l, r, "*") => Ok((l * r).asJson)
        case Req(_, _, op) => BadRequest(s"Unknown operator $op")
      }
    }
    .orNotFound
}

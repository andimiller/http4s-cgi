import cats.effect.IO
import net.andmiller.http4s.cgi.CgiApp
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes}

object HelloWorld extends CgiApp {
  override val routes: HttpApp[IO] = HttpRoutes
    .of[IO] { case GET -> Root / "hello" =>
      Ok("Hello world")
    }
    .orNotFound
}

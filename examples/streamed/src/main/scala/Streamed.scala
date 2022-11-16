import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeId
import net.andmiller.http4s.cgi.CgiApp
import org.http4s.{Entity, HttpApp, HttpRoutes, Response}

import scala.concurrent.duration.DurationInt

object Streamed extends CgiApp {
  override val routes: HttpApp[IO] = HttpRoutes
    .of[IO] { case _ =>
      Response[IO]()
        .withEntity(
          Entity(
            fs2.Stream.awakeEvery[IO](1.seconds).take(10).map(_.toString()).intersperse("\n").through(fs2.text.utf8.encode[IO])
          )
        )
        .pure[IO]
    }
    .orNotFound
}

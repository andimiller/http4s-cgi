import cats.implicits._
import cats.Monad
import cats.effect.{Concurrent, IO, Resource, Sync}
import io.circe.Json
import io.circe.syntax.EncoderOps
import net.andmiller.http4s.cgi.CgiApp
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes}
import fs2.io.file.{FileHandle, Files, Flag, Flags, ReadCursor, WriteCursor, Path => FPath}
import fs2.Stream
import io.circe.jawn.CirceSupportParser.facade
import org.typelevel.jawn.fs2._

import java.nio.file.{Path, Paths}

object OpenAndLockFile {
  def apply[F[_]: Concurrent: Files](path: Path): Resource[F, FileHandle[F]] = {
    Files[F].open(FPath.fromNioPath(path), Flags(Flag.Read, Flag.Write)).flatMap { channel =>
      Resource.make(channel.lock)(channel.unlock).as(channel)
    }
  }
}

object Counter {
  final val CHUNK_SIZE: 1024 = 1024

  def incrementAndGet[F[_]: Concurrent: Files](path: Path): F[Long] =
    OpenAndLockFile(path).use { file =>
      for {
        counter    <- ReadCursor(file, 0).readAll(CHUNK_SIZE).void.stream.through(fs2.text.utf8.decode[F]).runJsonOption[Json].flatMap { j =>
                        Concurrent[F].fromEither(
                          j.getOrElse(Json.fromLong(0)).as[Long]
                        )
                      }
        incremented = counter + 1
        _          <- file.truncate(0)
        _          <- WriteCursor(file, 0)
                        .writeAll(Stream.emit(incremented.asJson).map(_.noSpaces).through(fs2.text.utf8.encode[F]))
                        .void
                        .stream
                        .compile
                        .drain
      } yield incremented
    }
}

object HitCounter extends CgiApp {

  override val routes: HttpApp[IO] = HttpRoutes
    .of[IO] { case req =>
      Counter.incrementAndGet[IO](Paths.get("hits.json")).flatMap { hits => Ok(hits.asJson) }
    }
    .orNotFound
}

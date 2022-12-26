import cats.effect.kernel.Resource
import cats.effect._
import cats.implicits._
import com.github.sqlite4s.{SQLParts, SQLiteConnection, SQLiteStatement}
import net.andmiller.http4s.cgi.WebsocketdApp
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes, Request, Uri}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import fs2._
import fs2.io.file.{Path => FS2Path}
import fs2.io.file.Files
import fs2.io.file.Watcher.Event
import fs2.io.net.Socket

import java.io.File

object Name {
  def unapply[F[_]](req: Request[F]): Option[String] =
    req.uri.query.params.keys
      .find(_.toLowerCase == "name")
      .flatMap(req.uri.query.params.get)
      .filter(_.forall(_.isLetter))
}

trait Binding[T]                                             {
  def apply(s: SQLiteStatement, t: T): SQLiteStatement
}
object Binding                                               {
  def apply[T: Binding]: Binding[T] = implicitly

  // some instances
  implicit val bindUnit: Binding[Unit] = { case (s, _) => s }
}
trait Loader[T]                                              {
  def apply(s: SQLiteStatement): T
}
object Loader                                                {
  def apply[T: Loader]: Loader[T] = implicitly

  // some instances
  implicit val loadInt: Loader[Unit] = { _ => () }
}
class DbConn[F[_]: Sync](private val conn: SQLiteConnection) {

  /** Execute an SQL statement, using the Binding to bind inputs, and the Loader to bind outputs */
  def exec[I: Binding, O: Loader](
      sql: String
  )(i: I): F[Vector[O]] =
    Resource
      .make(Sync[F].delay {
        conn.prepare(sql)
      }) { s => Sync[F].delay { s.dispose() } }
      .use { statement =>
        Sync[F].delay { Binding[I].apply(statement, i) } *>
          Sync[F].delay { Loader[O].apply(statement) }.whileM[Vector](Sync[F].delay { statement.step() })
      }
}

object DB {
  def connect[F[_]: Sync](file: File, write: Boolean = false): Resource[F, DbConn[F]] =
    Resource
      .make(Sync[F].delay { new SQLiteConnection((file)) }) { c => Sync[F].delay { c.dispose() } }
      .evalTap { conn =>
        if (write) Sync[F].delay { conn.open() }
        else Sync[F].delay { conn.openReadonly() }
      }
      .map(new DbConn(_))
}

/** This is an example websocket server which lets people join and chat to each other via a sqlite db
  */
object WebsocketChat extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws =>
    HttpRoutes
      .of[IO] { case Name(name) =>
        ???
      }
      .orNotFound
}

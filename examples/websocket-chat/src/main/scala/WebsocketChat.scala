import cats.effect.kernel.Resource
import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits._
import com.github.sqlite4s.bindings.sqlite.SQLITE_CONSTANT.{SQLITE_DELETE, SQLITE_INSERT, SQLITE_UPDATE}
import com.github.sqlite4s.bindings.sqlite.{sqlite3_int64, sqlite3_update_hook}
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

import scalanative.unsafe.fromCString
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import scala.scalanative.unsafe.{CFuncPtr5, CInt, CQuote, CString, Ptr}

object Name {
  def unapply[F[_]](req: Request[F]): Option[String] =
    req.uri.query.params.keys
      .find(_.toLowerCase == "name")
      .flatMap(req.uri.query.params.get)
      .filter(_.forall(_.isLetter))
}

trait Binder[T] {
  def apply(s: SQLiteStatement, t: T): SQLiteStatement
}
object Binder   {
  def apply[T: Binder]: Binder[T] = implicitly

  // some instances
  implicit val bindUnit: Binder[Unit] = { case (s, _) => s }
}
trait Loader[T] {
  def apply(s: SQLiteStatement): T
}
object Loader   {
  def apply[T: Loader]: Loader[T] = implicitly

  // some instances
  implicit val loadInt: Loader[Unit] = { _ => () }
}

object DbConn                                                {
  val callbackStr: CString = c"callback"
}
class DbConn[F[_]: Sync](private val conn: SQLiteConnection) {

  def registerCallback(cb: AtomicInteger)(implicit dispatcher: Dispatcher[F]): F[Unit] = Sync[F].delay {
    println("registering callback")
    def callback(id: Ptr[Byte], op: CInt, db: CString, table: CString, row: sqlite3_int64): Unit = {
      println("callback triggered")
      // println(s"boop: ${op.toInt}, ${fromCString(db)}, ${fromCString(table)}, ${row.toLong}")
      val counter = cb.incrementAndGet()
      println(s"counter: $counter")
    }
    sqlite3_update_hook(
      conn.connectionHandle().asPtr(),
      callback(_, _, _, _, _),
      DbConn.callbackStr
    )
    println("callback registered")
  }.void

  /** Execute an SQL statement, using the Binder to bind inputs, and the Loader to bind outputs */
  def exec[I: Binder, O: Loader](
      sql: String
  )(i: I): F[Vector[O]] =
    Resource
      .make(Sync[F].delay {
        conn.prepare(sql)
      }) { s => Sync[F].delay { s.dispose() } }
      .use { statement =>
        Sync[F].delay { Binder[I].apply(statement, i) } *>
          Sync[F].delay { Loader[O].apply(statement) }.whileM[Vector](Sync[F].delay { statement.step() })
      }
}

object DB {
  def connect[F[_]: Sync](file: File, write: Boolean = false): Resource[F, DbConn[F]] =
    Resource
      .make(Sync[F].delay { new SQLiteConnection((file)) }) { c => Sync[F].delay { c.dispose() } }
      .evalTap { conn =>
        if (write) Sync[F].delay { conn.open(allowCreate = true) }
        else Sync[F].delay { conn.openReadonly() }
      }
      .map(new DbConn(_))
}

case class ChatLog(time: Instant, name: String, message: String)
object ChatLog {
  implicit val loader: Loader[ChatLog] = { s =>
    ChatLog(Instant.parse(s.columnString(0)), s.columnString(1), s.columnString(2))
  }
  implicit val binder: Binder[ChatLog] = { case (s, cl) =>
    s.bind(1, cl.time.toString()).bind(2, cl.name).bind(3, cl.message)
  }
}

object ChatLogDb {
  def provision[F[_]: Sync](conn: DbConn[F]): F[Unit] =
    conn.exec[Unit, Unit]("create table if not exists chat ( text time, text name, text message )")().void

  def readBacklog[F[_]: Sync](conn: DbConn[F]): F[Vector[ChatLog]] =
    conn.exec[Unit, ChatLog]("select * from chat order by time desc limit 5")()

  def send[F[_]: Sync](conn: DbConn[F])(log: ChatLog): F[Unit] =
    conn.exec[ChatLog, Unit]("insert into chat values (?, ?, ?)")(log).void
}

/** This is an example websocket server which lets people join and chat to each other via a sqlite db
  */
object WebsocketChat extends WebsocketdApp {
  override def create: WebSocketBuilder[IO] => HttpApp[IO] = ws =>
    HttpRoutes
      .of[IO] { case Name(name) =>
        ???
      /*
        val dbFile = new File("./chat.db")
        DB.connect[IO](dbFile, write = true).use(ChatLogDb.provision) *>
          DB.connect[IO](dbFile, write = false).use { conn => ??? }

       */
      }
      .orNotFound
}

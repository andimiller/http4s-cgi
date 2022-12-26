import cats.effect.IO
import cats.implicits._
import cats.effect.implicits._
import cats.effect.std.Dispatcher

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class SQLiteIntegrationTest extends munit.CatsEffectSuite {

  case class Cat(name: String, age: Int)
  implicit val catLoader: Loader[Cat] = { s =>
    Cat(s.columnString(0), s.columnInt(1))
  }
  implicit val catBinder: Binder[Cat] = { case (s, c) =>
    s.bind(1, c.name).bind(2, c.age)
  }

  test("use an sqlite database") {
    Dispatcher[IO].use { implicit dispatcher =>
      val counter = new AtomicInteger(0)
      DB.connect[IO](File.createTempFile("sqlite-integration-test", ".db"), write = true)
        .use { conn =>
          conn.registerCallback(counter) *>
            conn.exec[Unit, Unit]("create table if not exists cats ( varchar name primary key, int age)")() *>
            conn.exec[Cat, Unit]("insert into cats values (?, ?)")(Cat("bob", 12)) *>
            conn.exec[Unit, Cat]("select * from cats")() <* IO.println(s"change count: ${counter.get()}")
        }
        .assertEquals(
          Vector(Cat("bob", 12))
        )
    }
  }

}

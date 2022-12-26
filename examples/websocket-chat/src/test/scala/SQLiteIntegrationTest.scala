import cats.effect.IO
import cats.implicits._
import cats.effect.implicits._
import cats.effect.std.Dispatcher

import java.io.File

class SQLiteIntegrationTest extends munit.CatsEffectSuite {

  case class Cat(name: String, age: Int)
  implicit val catLoader: Loader[Cat] = { s =>
    Cat(s.columnString(0), s.columnInt(1))
  }
  implicit val catBinder: Binder[Cat] = { case (s, c) =>
    s.bind(1, c.name).bind(2, c.age)
  }

  def printString(s: String): Unit = println(s"other side of the callback: $s")

  test("use an sqlite database") {
    Dispatcher[IO].use { implicit dispatcher =>
      DB.connect[IO](File.createTempFile("sqlite-integration-test", ".db"), write = true)
        .use { conn =>
          conn.registerCallback(printString) *>
            conn.exec[Unit, Unit]("create table if not exists cats ( varchar name primary key, int age)")() *>
            conn.exec[Cat, Unit]("insert into cats values (?, ?)")(Cat("bob", 12)) *>
            conn.exec[Unit, Cat]("select * from cats")()
        }
        .assertEquals(
          Vector(Cat("bob", 12))
        )
    }
  }

}

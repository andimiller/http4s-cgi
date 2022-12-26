import cats.effect.IO

import cats.implicits._
import cats.effect.implicits._
import java.io.File

class SQLiteIntegrationTest extends munit.CatsEffectSuite {

  case class Cat(name: String, age: Int)
  implicit val catLoader: Loader[Cat] = { s =>
    Cat(s.columnString(0), s.columnInt(1))
  }

  test("use an sqlite database") {
    DB.connect[IO](new File("/tmp/test.db"))
      .use { conn =>
        conn.exec[Unit, Unit]("create table if not exists cats ( varchar name primary key, int age)")() *>
          conn.exec[Unit, Unit]("insert into cats values (\"bob\", 12)")() *>
          conn.exec[Unit, Cat]("select * from cats")()
      }
      .assertEquals(
        Vector(Cat("bob", 12))
      )
  }

}

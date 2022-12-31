import cats.effect.kernel.Resource
import cats.effect._
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits._
import fs2.concurrent.Topic
import io.chrisdavenport.rediculous.{RedisCommands, RedisConnection, RedisPubSub}
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.syntax.EncoderOps
import net.andmiller.http4s.cgi.WebsocketdApp
import org.http4s.dsl.io._
import org.http4s.{HttpApp, HttpRoutes, Request, Uri}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame

import java.time.Instant
import scala.concurrent.duration.{DurationDouble, DurationInt}
import io.chrisdavenport.rediculous._
import cats.implicits._
import cats.data._
import cats.effect._
import fs2.io.net._
import com.comcast.ip4s._
import io.chrisdavenport.rediculous.cluster.ClusterCommands
import scala.concurrent.duration._
import epollcat.EpollApp

object PubSubExample extends EpollApp {
  val all = "__keyspace*__:*"
  val foo = "foo"

  def run(args: List[String]): IO[ExitCode] = {
    RedisConnection.queued[IO].withHost(host"localhost").withPort(port"6379").withMaxQueued(10000).withWorkers(workers = 1).build.flatMap{
      connection => 
        RedisPubSub.fromConnection(connection, 4096)
    }.use{ alg => 
        alg.nonMessages({r => IO.println(s"other: $r")}) >>
        alg.unhandledMessages({r => IO.println(s"unhandled: $r")}) >>
        alg.psubscribe(all, {r => IO.println("p: " + r.toString())}) >>
        alg.subscribe(foo, {r  => IO.println("s: " + r.toString())}) >> {
          (
            alg.runMessages,
            Temporal[IO].sleep(10.seconds) >> 
            alg.subscriptions.flatTap(IO.println(_)) >> 
            alg.psubscriptions.flatTap(IO.println(_))
          ).parMapN{ case (_, _) => ()} 
        }
    }.as(ExitCode.Success)
    
  }

}

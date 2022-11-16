# http4s-cgi

This is an `http4s-server` backend which implements `CGI`, and is intended to be used with `scala-native`.

## Motivation

`AWS Lambda` is the hot new thing, and it seems like `CGI` to me, so I've implemented `CGI` for `http4s`.

## Example

```scala
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
```

## Building the examples

```shell
nix-shell -p clang
sbt -mem 4096 "project examples; nativeLink"
```

## Status

I'm not too sure I'd recommend using this, but it's very fast if you want to throw a few endpoints in a `CGI` compatible web server!
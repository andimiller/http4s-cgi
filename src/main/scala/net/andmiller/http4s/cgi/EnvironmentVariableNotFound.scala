package net.andmiller.http4s.cgi

class EnvironmentVariableNotFound(header: String) extends Throwable {
  override def getMessage: String = s"Expected '$header' environment variable but did not find it"
}

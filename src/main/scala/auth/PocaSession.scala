package auth

import com.softwaremill.session.{SessionSerializer, SingleValueSessionSerializer}

import scala.util.Try

case class PocaSession(username: String)

object PocaSession {
  implicit def serializer: SessionSerializer[PocaSession, String] =
    new SingleValueSessionSerializer(_.username,
      (uname: String) =>
        Try {
          PocaSession(uname)
        })
}
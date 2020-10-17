
package poca


import java.time.LocalDateTime

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._
import java.util.UUID

case class User(userId: String, username: String, userPassword: String = "", userMail: String ="", userLastConnection: LocalDateTime)

final case class UserAlreadyExistsException(private val message: String="", private val cause: Throwable=None.orNull)
    extends Exception(message, cause)
final case class InconsistentStateException(private val message: String="", private val cause: Throwable=None.orNull)
    extends Exception(message, cause)
class UsersTable(tag: Tag) extends Table[(User)](tag, "users") {
    def userId = column[String]("userId", O.PrimaryKey)
    def username = column[String]("username")
    def userPassword = column[String]("userPassword")
    def userMail = column[String]("userMail")
    def userLastConnection = column[LocalDateTime]("userLastConnection")
    def * = (userId, username,userPassword,userMail,userLastConnection) <> (User.tupled,User.unapply)
}
class Users {
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    val db = MyDatabase.db
    val users = TableQuery[UsersTable]

    def createUser(username: String): Future[Unit] = {
        val existingUsersFuture = getUserByUsername(username)

        existingUsersFuture.flatMap(existingUser => {
            if (existingUser.isEmpty) {
                val userId = UUID.randomUUID.toString
                val newUser = User(userId, username, "", "", LocalDateTime.now())
                val dbio: DBIO[Int] = users += newUser
                var resultFuture: Future[Int] = db.run(dbio)

                // We do not care about the Int value
                resultFuture.map(_ => ())
            } else {
                throw new UserAlreadyExistsException(s"A user with username '$username' already exists.")
            }
        })
    }

    def getUserByUsername(username: String): Future[Option[User]] = {
        val query = users.filter(_.username === username)

        val userListFuture = db.run(query.result)

        userListFuture.map((userList) => {
            userList.length match {
                case 0 => None
                case 1 => Some(userList.head)
                case _ => throw new InconsistentStateException(s"Username $username is linked to several users in database!")
            }
        })
    }

    def getAllUsers: Future[Seq[User]] = {
        val userListFuture = db.run(users.result)

        userListFuture.map((userList) => {
            userList
        })
    }
}

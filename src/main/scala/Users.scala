
package poca

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import slick.jdbc.PostgresProfile.api._

case class User(userId: String,
				username: String,
				userPassword: String = "",
				userMail: String ="",
				userLastConnection: LocalDateTime,
				roleId: Option[Int])

case class UserWithRole(user: User, role: Role)

final case class UserAlreadyExistsException(private val message: String="", private val cause: Throwable=None.orNull)
    extends Exception(message, cause)
final case class UserDoesntExistException(private val message: String="", private val cause: Throwable=None.orNull)
  extends Exception(message, cause)

class UsersTable(tag: Tag) extends Table[(User)](tag, "users") {
    def userId = column[String]("userId", O.PrimaryKey)
    def username = column[String]("username")
    def userPassword = column[String]("userPassword")
    def userMail = column[String]("userMail")
    def userLastConnection = column[LocalDateTime]("userLastConnection")
    def roleId = column[Option[Int]]("userRoleId")
    def * = (userId, username,userPassword,userMail,userLastConnection, roleId) <> (User.tupled,User.unapply)
    def role = foreignKey("role", roleId, TableQuery[RolesTable])(_.roleId)
}
class Users {
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    val db = MyDatabase.db
    val users = TableQuery[UsersTable]
    val rolesTable = TableQuery[RolesTable]

    def createUser(username: String, role: Option[Role]) = {
        val userId =  UUID.randomUUID.toString

        def persistNewUser(roleId: Option[Int]) = {
            val newUser = User(userId, username, "", "", LocalDateTime.now(), roleId)
            val dbio: DBIO[Int] = users += newUser
            var resultFuture: Future[Int] = db.run(dbio)
            resultFuture.map(_ => userId)
        }

        val existingUsersFuture = getUserByUsername(username)
        existingUsersFuture.flatMap(existingUser => {
            if (existingUser.isEmpty) {
            	if(!role.isEmpty) {
            		val roles: Roles = new Roles()
            		val roleName = role.last.roleName
            		val existingRoleFuture = roles.getRoleByName(roleName)
            		existingRoleFuture.flatMap(existingRole => {
				        if (existingRole.isEmpty) {
				        	throw new RoleDoesntExistsException(s"There is no role named '$roleName'.")
				        }
				        else {
				           	persistNewUser(existingRole.last.roleId).map(_ => userId)
				        }
				    })
				}
            	else {
                	persistNewUser(None).map(_ => userId)
                }
            } 
            else {
                throw new UserAlreadyExistsException(s"A user with username '$username' already exists.")
            }
        })
    }

    def getUserByUsernameWithRole(username: String) = {
    	val tupledJoin = users filter(_.username===username) join rolesTable on (_.roleId === _.roleId)
    	db.run(tupledJoin.result).map(_.map(UserWithRole.tupled))
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

    def getUserByIdWithRole(userId: String) = {
    	val tupledJoin = users filter(_.userId===userId) join rolesTable on (_.roleId === _.roleId)
    	db.run(tupledJoin.result).map(_.map(UserWithRole.tupled))
    }

    def getUserById(userId: String): Future[Option[User]] = {
        val query = users.filter(_.userId === userId)

        val userListFuture = db.run(query.result)

        userListFuture.map((userList) => {
            userList.length match {
                case 0 => None
                case 1 => Some(userList.head)
                case _ => throw new InconsistentStateException(s"User ID $userId is linked to several users in database!")
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

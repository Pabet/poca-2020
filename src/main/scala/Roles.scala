package poca

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._

case class Role(roleId: Option[Int], roleName: String)

final case class RoleDoesntExistsException(private val message: String="", private val cause: Throwable=None.orNull)
  extends Exception(message, cause)

class RolesTable(tag: Tag) extends Table[(Role)](tag, "roles") {
  def roleId = column[Int]("roleId", O.PrimaryKey, O.AutoInc)
  def roleName = column[String]("roleName")
  def * = (roleId.?,roleName) <> (Role.tupled,Role.unapply)
}

class Roles{
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val db = MyDatabase.db
  val roles = TableQuery[RolesTable]

  def createRole(roleName: String): Future[Unit] = {
    val newRole = Role(None,roleName)
    val dbio: DBIO[Int] = roles += newRole
    var resultFuture: Future[Int] = db.run(dbio)
    resultFuture.map(_ => ())
  }

  def getAllRoles: Future[Seq[Role]] = {
    val roleListFuture =  db.run(roles.result)
    roleListFuture.map((roleList) => {
      roleList
    })
  }

  def getRoleByName(roleName: String): Future[Option[Role]] = {
    val query = roles.filter(_.roleName === roleName)

    val roleListFuture = db.run(query.result)

    roleListFuture.map((roleList) => {
      roleList.length match {
        case 0 => None
        case 1 => Some(roleList.head)
        case _ => throw new InconsistentStateException(s"Role name $roleName is linked to several roles in database!")
      }
    })
  }
}
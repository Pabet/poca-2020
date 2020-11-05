package migrations

import poca.{CartsTable, ProductsTable, RolesTable, UsersTable, Product, Role, User, Migration, MyDatabase}

import com.typesafe.scalalogging.LazyLogging
import java.time.LocalDateTime
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

class Migration05NewTablesAndJoins (db: Database) extends Migration with LazyLogging {
  
  class CurrentRolesTable(tag: Tag) extends Table[(Option[Int],String)](tag, "roles") {
    def roleId = column[Int]("roleId", O.PrimaryKey, O.AutoInc)
    def roleName = column[String]("roleName")
    def * = (roleId.?,roleName)
  }

  class CurrentUsersTable(tag: Tag) extends Table[(String,String,String,String,LocalDateTime,Option[Int])](tag, "users") {
    def userId = column[String]("userId", O.PrimaryKey)
    def username = column[String]("username")
    def userPassword = column[String]("userPassword")
    def userMail = column[String]("userMail")
    def userLastConnection = column[LocalDateTime]("userLastConnection")
    def roleId = column[Option[Int]]("userRoleId")
    def * = (userId, username,userPassword,userMail,userLastConnection, roleId)
    def role = foreignKey("role", roleId, TableQuery[RolesTable])(_.roleId)
  }

  class CurrentJCartProductTable(tag: Tag) extends Table[(Int,String,Int)](tag, "j_cart_product") {
    def cartId = column[Int]("cartId")
    def productId = column[String]("productId")
    def productQuantity = column[Int]("productQuantity")
    def cart = foreignKey("cart", cartId, TableQuery[CartsTable])(_.cartId)
    def product = foreignKey("product", productId, TableQuery[ProductsTable])(_.productId)
    def pk = primaryKey("pk_cart_product", (cartId, productId))
    def * = (cartId, productId, productQuantity)
  }

  class CurrentCartsTable(tag: Tag) extends Table[(Option[Int],String,LocalDateTime)](tag, "carts") {
    def cartId = column[Int]("cartId", O.PrimaryKey, O.AutoInc)
    def userId = column[String]("userId")
    def cartDate = column[LocalDateTime]("cardDate")
    def user = foreignKey("user",userId,TableQuery[UsersTable])(_.userId)
    def * = (cartId.?,userId,cartDate)
  }

  override def apply(): Unit = {
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    val carts = TableQuery[CurrentCartsTable]
    val jCartProducts = TableQuery[CurrentJCartProductTable]
    val roles = TableQuery[RolesTable]
    val users = TableQuery[CurrentUsersTable]
    val dbio = DBIO.seq(
      jCartProducts.schema.dropIfExists,
      carts.schema.dropIfExists,
      users.schema.dropIfExists,
      roles.schema.dropIfExists,
      roles.schema.create,
      users.schema.create,
      carts.schema.create,
      jCartProducts.schema.create,
      roles += Role(roleId = Some(1), roleName = "User"),
      roles += Role(roleId = Some(2), roleName = "Admin"),
      users += ("0","Pierre","superMdp","mail@test.com",LocalDateTime.now(), Some(1)),
      users += ("1","Paul","superMdp","mail@test.com",LocalDateTime.now(), Some(1)),
      users += ("2","Jacques","superMdp","mail@test.com",LocalDateTime.now(), Some(1)),
      users += ("3","Admin","superMdp","mail@test.com",LocalDateTime.now(), Some(2)),
      carts +=(Some(1),"0",LocalDateTime.now()),
      carts +=(Some(2),"1",LocalDateTime.now()),
      carts +=(Some(3),"2",LocalDateTime.now()),
      jCartProducts += (1,"0",3),
      jCartProducts += (2,"1",2),
      jCartProducts += (3,"2",15)
    )
    var resultFuture = db.run(dbio)
    // We do not care about the Int value
    Await.result(resultFuture, Duration.Inf)
    logger.info("Done creating Role and updating User Table and data")
  }
}

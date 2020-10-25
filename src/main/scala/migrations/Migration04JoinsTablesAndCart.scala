package migrations

import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import poca.{CartsTable, Migration, MyDatabase, Product, ProductsTable, User, UsersTable}
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class Migration04JoinsTablesAndCart(db: Database) extends Migration with LazyLogging{

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
    val jCartProducts = TableQuery[CurrentJCartProductTable]
    val carts = TableQuery[CurrentCartsTable]
    val dbio = DBIO.seq(
      carts.schema.create,
      jCartProducts.schema.create,
      carts +=(Some(1),"0",LocalDateTime.now()) ,
      carts +=(Some(2),"1",LocalDateTime.now()),
      carts +=(Some(3),"2",LocalDateTime.now()) ,
      jCartProducts += (1,"0",3),
      jCartProducts += (2,"1",2),
      jCartProducts += (3,"2",15)
    )
    var resultFuture = db.run(dbio)
    // We do not care about the Int value
    Await.result(resultFuture, Duration.Inf)
    logger.info("Done populating users and products with fake data")
  }
}

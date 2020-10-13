
package poca

import java.time.LocalDateTime

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._


class Migration01CreateTables(db: Database) extends Migration with LazyLogging {
    class CurrentUsersTable(tag: Tag) extends Table[User](tag, "users") {
        def userId = column[String]("userId", O.PrimaryKey)
        def username = column[String]("username")
        def userPassword = column[String]("userPassword")
        def userMail = column[String]("userMail")
        def userLastConnection = column[LocalDateTime]("userLastConnection")
        def * = (userId, username,userPassword,userMail,userLastConnection) <> (User.tupled,User.unapply)
    }
    class CurrentProductsTable(tag: Tag) extends Table[Product](tag, "products") {
        def productId = column[String]("productId", O.PrimaryKey)
        def productName = column[String]("productName")
        def productPrice = column[Double]("productPrice")
        def productDetail = column[String]("productDetail")
        def categoryId = column[String]("productCategoryId")
        def * = (productId, productName, productPrice, productDetail) <> (Product.tupled,Product.unapply)
    }

    override def apply(): Unit = {
        implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
        val users = TableQuery[CurrentUsersTable]
        val products = TableQuery[CurrentProductsTable]
        val dbio: DBIO[Unit] = users.schema.create
        dbio.andThen(products.schema.create)
        val creationFuture: Future[Unit] = db.run(dbio)
        Await.result(creationFuture, Duration.Inf)
        logger.info("Done creating table Users and Products")
    }
}
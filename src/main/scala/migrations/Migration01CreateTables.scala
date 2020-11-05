
package poca

import java.time.LocalDateTime

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._


class Migration01CreateTables(db: Database) extends Migration with LazyLogging {

    class CurrentUsersTable(tag: Tag) extends Table[(String,String,String,String,LocalDateTime)](tag, "users") {
        def userId = column[String]("userId", O.PrimaryKey)
        def username = column[String]("username")
        def userPassword = column[String]("userPassword")
        def userMail = column[String]("userMail")
        def userLastConnection = column[LocalDateTime]("userLastConnection")
        def roleId = column[String]("userRoleId")
        def * = (userId, username,userPassword,userMail,userLastConnection)
    }

    class CurrentProductsTable(tag: Tag) extends Table[(String,String,Double,String)](tag, "products") {
        def productId = column[String]("productId", O.PrimaryKey)
        def productName = column[String]("productName")
        def productPrice = column[Double]("productPrice")
        def productDetail = column[String]("productDetail")
        def categoryId = column[String]("productCategoryId")
        def * = (productId, productName, productPrice, productDetail)
    }

    override def apply(): Unit = {
        implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
        val users = TableQuery[CurrentUsersTable]
        val products = TableQuery[CurrentProductsTable]
        val dbio: DBIO[Unit] = DBIO.seq(
            users.schema.create,
            products.schema.create
        )
        val creationFuture: Future[Unit] = db.run(dbio)
        Await.result(creationFuture, Duration.Inf)
        logger.info("Done creating table Users and Products")
    }
}
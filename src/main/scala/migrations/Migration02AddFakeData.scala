package migrations

import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import poca.{Migration, MyDatabase, Product, ProductsTable, User, UsersTable}
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class Migration02AddFakeData(db: Database) extends Migration with LazyLogging{

  class CurrentUsersTable(tag: Tag) extends Table[(String,String,String,String,LocalDateTime)](tag, "users") {
    def userId = column[String]("userId", O.PrimaryKey)
    def username = column[String]("username")
    def userPassword = column[String]("userPassword")
    def userMail = column[String]("userMail")
    def userLastConnection = column[LocalDateTime]("userLastConnection")
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
    val dbio = DBIO.seq(
      users += ("0","Pierre","superMdp","mail@test.com",LocalDateTime.now()),
      users += ("1","Paul","superMdp","mail@test.com",LocalDateTime.now()),
      users += ("2","Jacques","superMdp","mail@test.com",LocalDateTime.now()),
      products += ("0","Chantilly",232.40,"La chantilly des vrais champions (recommandé par l'OMS)"),
      products += ("1","Voiture de sport",23.40,"Une voiture pour faire du sport, elle fonctionne pas va prendre ton vélo"),
      products += ("2","Vélo",657.40,"Un truc avec une scelle, deux pédales et 2 roues"),
      products += ("3","Aspirateur",99.99,"Un truc qui aspire d'autres trucs. Fonctionne avec des sacs"),
      products += ("4","Masque anti Covid-19",2.33,"Certifié AFNOR, juré le virus il passe pas")
    )
    var resultFuture = db.run(dbio)
    // We do not care about the Int value
    Await.result(resultFuture, Duration.Inf)
    logger.info("Done populating users and products with fake data")
  }
}

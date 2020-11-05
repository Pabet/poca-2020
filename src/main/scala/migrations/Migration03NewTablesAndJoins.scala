package migrations

import poca.{CategoriesTable, Category, Migration, MyDatabase, Product}
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{Await, ExecutionContext, Future}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.Duration


class Migration03NewTablesAndJoins (db: Database) extends Migration with LazyLogging {

  class CurrentCategoriesTable(tag: Tag) extends Table[(Option[Int],String)](tag, "categories") {
    def categoryId = column[Int]("categoryId", O.PrimaryKey, O.AutoInc)
    def categoryName = column[String]("categoryName")
    def * = (categoryId.?,categoryName)
  }

  class CurrentProductsTable(tag: Tag) extends Table[(String,String,Double,String,Option[Int])](tag, "products") {
    def productId = column[String]("productId", O.PrimaryKey)
    def productName = column[String]("productName")
    def productPrice = column[Double]("productPrice")
    def productDetail = column[String]("productDetail")
    def categoryId = column[Option[Int]]("productCategoryId")
    def * = (productId, productName, productPrice, productDetail,categoryId)
    def category = foreignKey("category",categoryId,TableQuery[CategoriesTable])(_.categoryId)
  }

  override def apply(): Unit = {
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    val categories = TableQuery[CategoriesTable]
    val products = TableQuery[CurrentProductsTable]
    val dbio = DBIO.seq(
      categories.schema.create,
      products.schema.dropIfExists,
      products.schema.create,
      categories += Category(categoryId = Some(1), categoryName = "Food"),
      categories += Category(categoryId = Some(2), categoryName = "Car"),
      categories += Category(categoryId = Some(3), categoryName = "Sport"),
      categories += Category(categoryId = Some(4), categoryName = "Home appliance"),
      categories += Category(categoryId = Some(5), categoryName = "Medical"),
      products += ("0","Chantilly",232.40,"La chantilly des vrais champions (recommandé par l'OMS)",Some(1)),
      products += ("1","Voiture de sport",23.40,"Une voiture pour faire du sport, elle fonctionne pas va prendre ton vélo",Some(2)),
      products += ("2","Vélo",657.40,"Un truc avec une scelle, deux pédales et 2 roues",Some(3)),
      products += ("3","Aspirateur",99.99,"Un truc qui aspire d'autres trucs. Fonctionne avec des sacs",Some(4)),
      products += ("4","Masque anti Covid-19",2.33,"Certifié AFNOR, juré le virus il passe pas",Some(5))
    )
    var resultFuture = db.run(dbio)
    // We do not care about the Int value
    Await.result(resultFuture, Duration.Inf)
    logger.info("Done creating Category and updating Product Table and data")
  }
}

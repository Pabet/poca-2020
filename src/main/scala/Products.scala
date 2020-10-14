package poca

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._
import java.util.UUID
import slick.jdbc.PostgresProfile.api._

case class Product(productId: String,
                    productName: String,
                    productPrice: Double,
                    productDetail: String)

final case class IncorrectPriceException(private val message: String = "", private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

class ProductsTable(tag: Tag) extends Table[Product](tag, "products") {
  def productId = column[String]("productId", O.PrimaryKey)
  def productName = column[String]("productName")
  def productPrice = column[Double]("productPrice")
  def productDetail = column[String]("productDetail")
  def categoryId = column[String]("productCategoryId")
  def * = (productId, productName, productPrice, productDetail) <> (Product.tupled,Product.unapply)
}

class Products {
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val db = MyDatabase.db
  val products = TableQuery[ProductsTable]

  def createProduct(productName: String, productPrice: Double, productDetail: String): Future[Unit] = {
    if(productPrice < 0) {
      throw new IncorrectPriceException(s"Cannot insert product '$productName' with a price < 0 : $productPrice")
    }

    val productId = UUID.randomUUID.toString
    val newProduct = Product(productId=productId, productName=productName, productPrice=productPrice, productDetail=productDetail)
    val newProductAsTuple: (String, String, Double, String) = Product.unapply(newProduct).get

    val dbio: DBIO[Int] = products += newProduct
    var resultFuture: Future[Int] = db.run(dbio)
    resultFuture.map(_ => ())
  }

  def getAllProducts: Future[Seq[Product]] = {
    db.run(products.result)
  }

}
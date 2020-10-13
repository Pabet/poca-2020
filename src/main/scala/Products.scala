package poca

import scala.language.postfixOps
import slick.jdbc.PostgresProfile.api._

case class Product(productId: String,
                    productName: String,
                    productPrice: Double,
                    productDetail: String)
class ProductsTable(tag: Tag) extends Table[Product](tag, "products") {
  def productId = column[String]("productId", O.PrimaryKey)
  def productName = column[String]("productName")
  def productPrice = column[Double]("productPrice")
  def productDetail = column[String]("productDetail")
  def categoryId = column[String]("productCategoryId")
  def * = (productId, productName, productPrice, productDetail) <> (Product.tupled,Product.unapply)
}
class Products {

}
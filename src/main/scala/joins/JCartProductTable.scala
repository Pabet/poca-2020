package joins
import poca.{CartsTable, ProductsTable,Product}
import slick.jdbc.PostgresProfile.api._

case class CartProductDTO(cartId: Int, productId : String, productQuantity: Int)
case class CartProductLine(product: Product, quantity: Int)

class JCartProductTable(tag: Tag) extends Table[CartProductDTO](tag, "j_cart_product") {
  def cartId = column[Int]("cartId")
  def productId = column[String]("productId")
  def productQuantity = column[Int]("productQuantity")
  def cart = foreignKey("cart",cartId,TableQuery[CartsTable])(_.cartId)
  def product = foreignKey("product",productId,TableQuery[ProductsTable])(_.productId)
  def pk = primaryKey("pk_cart_product", (cartId, productId))
  def * = (cartId,productId,productQuantity) <> (CartProductDTO.tupled,CartProductDTO.unapply)
}

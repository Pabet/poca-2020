package joins
import poca.{CartsTable, ProductsTable, UsersTable}
import slick.jdbc.PostgresProfile.api._

class JUserCartTable(tag: Tag) extends Table[(String,Int)](tag, "j_cart_product") {
  def userId = column[String]("userId")
  def cartId = column[Int]("cartId")
  def user = foreignKey("user",userId,TableQuery[UsersTable])(_.userId)
  def cart = foreignKey("cart",cartId,TableQuery[CartsTable])(_.cartId)
  def pk = primaryKey("pk_user_cart", (userId, cartId))
  def idx = index("unique_cart", (cartId), unique = true)
  def * = (userId,cartId)
}

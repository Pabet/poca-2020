package poca

import java.time.LocalDateTime

import joins.{CartProductDTO, CartProductLine, JCartProductTable}

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._


case class Cart(cartId: Option[Int], userId : String, cartDate: LocalDateTime)
case class CartWithProducts(cart : Cart, products : Seq[CartProductLine])

final case class CartDoesntExistException(private val message: String="", private val cause: Throwable=None.orNull)
  extends Exception(message, cause)

final case class CartProductJointError(private val message: String = "", private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

class CartsTable(tag: Tag) extends Table[(Cart)](tag, "carts") {
  def cartId = column[Int]("cartId", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("userId")
  def cartDate = column[LocalDateTime]("cardDate")
  def user = foreignKey("user",userId,TableQuery[UsersTable])(_.userId)
  def * = (cartId.?,userId,cartDate) <> (Cart.tupled,Cart.unapply)
}

class Carts {
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val db = MyDatabase.db
  val cartsTable = TableQuery[CartsTable]
  val jCartProductTable = TableQuery[JCartProductTable]
  val productsTable = TableQuery[ProductsTable]

  // Return a Future seq of (Product,Quantity) aka CartProductLine case class
  def getCartProductLines(cartId : Int) : Future[Seq[CartProductLine]] ={
    val cartProductEntriesFuture = db.run(jCartProductTable.filter(_.cartId===cartId).join(productsTable).on(_.productId===_.productId).result)
    cartProductEntriesFuture.map(tupleList => {
        tupleList.map {
          case (CartProductDTO(_,_,quantity),product) => CartProductLine(product,quantity)
          case _ => throw CartProductJointError(s"Unable to create tuple (product,quantity) from '${tupleList.toString()}'")
        }
    })
  }

  def createCart(userId: String) = {
    val users = new Users()
    val existingUserFuture = users.getUserById(userId)
    existingUserFuture.flatMap(
      existingUser => {
        if (existingUser.isEmpty) {
          throw new UserDoesntExistException(s"Cannot create cart, there is no user with ID '$userId'.")
        } else {
          val newCart = Cart(cartId = None, userId = userId, cartDate = LocalDateTime.now())
          val dbio: DBIO[Int] = cartsTable += newCart
          var resultFuture: Future[Int] = db.run(dbio)
          resultFuture.map(_ => ())
        }
      }
    )
  }

  def getAllCarts= {
    val cartListFuture = db.run(cartsTable.result)

    cartListFuture.map((cartList) => {
      cartList
    })
  }

  def getAllCartsForUser(userId: String)= {
    val cartListFuture = db.run(cartsTable.filter(_.userId === userId).result)
    cartListFuture.map((cartList) => {
      cartList
    })
  }

  def getLastCartForUser(userId: String) ={
    val cartListFuture = db.run(cartsTable.filter(_.userId === userId).sortBy(_.cartDate).result)
    cartListFuture.map((cartList) => {
      cartList.last
    })
  }

  // Return a Future of a CarWithProduct
  def getCartWithProducts(cartId: Int) = {
    val cartWithProductsFuture = db.run(cartsTable.filter(_.cartId === cartId).result)
    cartWithProductsFuture.flatMap(cartList => {
      cartList.length match {
        case 0 => throw new CartDoesntExistException(s"Cart id $cartId does not exist in database!")
        case 1 => getCartProductLines(cartId).map(cartProductLines => {
          CartWithProducts(cartList.last,cartProductLines)
        })
        case _ => throw new InconsistentStateException(s"Cart id $cartId is linked to several carts in database!")
      }
    })
  }

  def getCartById(cartId: Int)= {
    val query = cartsTable.filter(_.cartId === cartId)

    val cartListFuture = db.run(query.result)

    cartListFuture.map((cartList) => {
      cartList.length match {
        case 0 => None
        case 1 => Some(cartList.head)
        case _ => throw new InconsistentStateException(s"Cart ID $cartId is linked to several carts in database!")
      }
    })
  }

  def addProductToCart(cartId : Int, productId : String, productQuantity: Int): Unit ={
    val products = new Products()
    val existingCartFuture = getCartById(cartId)
    existingCartFuture.flatMap(
      existingCart => {
        if (existingCart.isEmpty) {
          throw new CartDoesntExistException(s"Cannot add product to cart, there is no cart with ID '$cartId'.")
        } else {
          val existingProductFuture = products.getProductById(productId)
          existingProductFuture.flatMap( existingProduct => {
            if (existingProduct.isEmpty) {
              throw new ProductDoesntExistException(s"Cannot add product to cart, there is no product with ID '$productId'.")
            } else {
              val newCartProductDTO = CartProductDTO(cartId = cartId, productId = productId, productQuantity = productQuantity)
              val dbio: DBIO[Int] = jCartProductTable += newCartProductDTO
              var resultFuture: Future[Int] = db.run(dbio)
              resultFuture.map(_ => ())
            }
          })
        }
      }
    )
  }

}
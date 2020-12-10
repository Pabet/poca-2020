package poca

import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpResponse,
  StatusCodes
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes.Found
import com.typesafe.scalalogging.LazyLogging
import play.twirl.api.HtmlFormat
import scala.concurrent.{ExecutionContext, Future}
import TwirlMarshaller._
import scala.util.Failure

trait CartRoutes extends LazyLogging {

  implicit val executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  def getUserCarts(
      users: Users,
      cart: Carts,
      username: String
  ): Future[HtmlFormat.Appendable] = {
    val r = for {
      potentialUid <- users.getUserByUsername(username)
      lastCart <- cart.getLastCartForUser(potentialUid match {
        case Some(u) => u.userId
        case None =>
          throw new IllegalArgumentException(
            s"Unable to find the user: $username"
          )
      })
      cartContent <- cart.getCartProductLines(lastCart.cartId match {
        case Some(x) => x
        case None =>
          throw new IllegalArgumentException(
            "Unable to find a cart for the user"
          )
      })
    } yield html.cart(
      lastCart.cartId match {
        case Some(x) => x
        case None =>
          throw new IllegalArgumentException(
            "Unable to find a cart for the user"
          )
      },
      cartContent
    )
    r.map { x => x };
  }

  def removeProductFromCart(
      users: Users,
      cart: Carts,
      username: String,
      fields: Map[String, String]
  ): Future[HtmlFormat.Appendable] = {

    var actionFuture = for {
      processAction <- fields.get("action") match {
        case Some("decrease") =>
          cart
            .removeProductFromCart(
              fields.get("cartId") match {
                case Some(x) => x.toInt
                case None =>
                  throw new IllegalArgumentException("Expected cart id")
              },
              fields.get("productId") match {
                case Some(x) => x
                case None =>
                  throw new IllegalArgumentException("Expected product id")
              }
            )
        case Some("increase") =>
          cart
            .changeAmountOfProductInCart(
              fields.get("cartId") match {
                case Some(x) => x.toInt
                case None =>
                  throw new IllegalArgumentException("Expected cart id")
              },
              fields.get("productId") match {
                case Some(x) => x
                case None =>
                  throw new IllegalArgumentException("Expected product id")
              },
              fields.get("productQuantity") match {
                case Some(x) => x.toInt + 1
                case None =>
                  throw new IllegalArgumentException(
                    "Expected product quantity"
                  )
              }
            )
        case Some("delete") =>
          cart.changeAmountOfProductInCart(
            fields.get("cartId") match {
              case Some(x) => x.toInt
              case None =>
                throw new IllegalArgumentException("Expected cart id")
            },
            fields.get("productId") match {
              case Some(x) => x
              case None =>
                throw new IllegalArgumentException("Expected product id")
            },
            0
          )
        case Some(x) =>
          throw new IllegalArgumentException("Unable to handle action")
        case None =>
          throw new IllegalArgumentException("No action specify in request")
      }
    } yield getUserCarts(
      users,
      cart,
      username
    )

    actionFuture.flatMap { x => x };
  }
}

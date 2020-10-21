package poca

import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.server.Directives.{complete, concat, formFieldMap, get, path, post}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import com.typesafe.scalalogging.LazyLogging
import TwirlMarshaller._
import play.twirl.api.HtmlFormat


class Routes(users: Users, products: Products) extends LazyLogging {
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  def getHello(): HttpEntity.Strict = {
    logger.info("I got a request to greet.")
    HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      "<h1>Say hello to akka-http</h1><p>A marketpace developped by Dirty Picnic Tractors</p>"
    )
  }

  def getSignIn: HtmlFormat.Appendable = {
    logger.info("I got a request for signin.")
    html.signin()
  }

  def getSignup(): HtmlFormat.Appendable = {
    logger.info("I got a request for signup.")
    html.signup()
  }

  def register(fields: Map[String, String]): Future[HttpResponse] = {
    logger.info("I got a request to register.")

    fields.get("username") match {
      case Some(username) => {
        val userCreation: Future[Unit] = users.createUser(username = username)

        userCreation.map(_ => {
          HttpResponse(
            StatusCodes.OK,
            entity = s"Welcome '$username'! You've just been registered to our great marketplace.",
          )
        }).recover({
          case exc: UserAlreadyExistsException => {
            HttpResponse(
              StatusCodes.OK,
              entity = s"The username '$username' is already taken. Please choose another username.",
            )
          }
        })
      }
      case None => {
        Future(
          HttpResponse(
            StatusCodes.BadRequest,
            entity = "Field 'username' not found."
          )
        )
      }
    }
  }

  def getUsers(): Future[HtmlFormat.Appendable] = {
    logger.info("I got a request to get user list.")

    val userSeqFuture: Future[Seq[User]] = users.getAllUsers

    userSeqFuture.map(userSeq => html.users(userSeq))
  }

  def getProducts(): Future[HtmlFormat.Appendable] = {
    logger.info("I got a request to get product list.")

    val productSeqFuture: Future[Seq[Product]] = products.getAllProducts

    productSeqFuture.map(productSeq => html.products(productSeq))
  }

  def buyProduct(fields: Map[String, String]): Future[HtmlFormat.Appendable] = {
    val productId = fields.get("id").orNull
    logger.info(s"I got a request to buy the product $productId");

    val productFuture: Future[Product] = Future {
      Product(
        productId = fields.get("id").orNull,
        productName = fields.get("name").orNull,
        productPrice = fields.get("price").map(f => f.toDouble).get,
        productDetail = fields.get("detail").orNull,
        categoryId = None
      )
    }

    Future(
      HttpResponse(
        StatusCodes.OK,
        entity = s"Thank you ! you bought '$productId'"
      )
    )

    productFuture.map(product => html.purchase(product))
  }

  val routes: Route =
    concat(
      path("hello") {
        get {
          complete(getHello)
        }
      },
      path("signin") {
        get {
          complete(getSignIn)
        }
      },
      path("signup") {
        get {
          complete(getSignup)
        }
      },
      path("register") {
        (post & formFieldMap) { fields =>
          complete(register(fields))
        }
      },
      path("users") {
        get {
          complete(getUsers)
        }
      },
      path("products") {
        get {
          complete(getProducts)
        }
      },
      path("purchase") {
        (post & formFieldMap) { fields =>
          complete(buyProduct(fields))
        }
      }
    )

}

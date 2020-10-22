package poca

import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpResponse,
  StatusCodes
}
import akka.http.scaladsl.server.Directives.{
  complete,
  concat,
  formFieldMap,
  get,
  path,
  post
}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import play.twirl.api.HtmlFormat
import scala.concurrent.{ExecutionContext, Future}
import TwirlMarshaller._

class Routes(users: Users, products: Products)
    extends UserRoutes
    with ProductRoutes {

  override implicit val executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  def getHello(): HttpEntity.Strict = {
    logger.info("I got a request to greet.")
    HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      "<h1>Say hello to akka-http</h1><p>A marketpace developped by Dirty Picnic Tractors</p>"
    )
  }

  val routes: Route =
    concat(
      path("hello") {
        get {
          complete(getHello())
        }
      },
      path("signin") {
        get {
          complete(super[UserRoutes].getSignIn(users))
        }
      },
      path("signup") {
        get {
          complete(super[UserRoutes].getSignup(users))
        }
      },
      path("register") {
        (post & formFieldMap) { fields =>
          complete(super[UserRoutes].register(users, fields))
        }
      },
      path("users") {
        get {
          complete(super[UserRoutes].getUsers(users))
        }
      },
      path("products") {
        get {
          complete(super[ProductRoutes].getProducts(products))
        }
      },
      path("products") {
        (post & formFieldMap) { fields =>
          complete(super[ProductRoutes].addProduct(products, fields))
        }
      },
      path("purchase") {
        (post & formFieldMap) { fields =>
          complete(super[ProductRoutes].buyProduct(products, fields))
        }
      }
    )

}

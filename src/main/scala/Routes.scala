package poca

import akka.http.scaladsl.server.Directives._
import java.time.LocalDateTime

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes, HttpMessage}
import akka.http.scaladsl.server.Directives.{authenticateBasic, complete, concat, formFieldMap, get, path, post}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.client.RequestBuilding.Get
import com.typesafe.scalalogging.LazyLogging
import play.twirl.api.HtmlFormat

import scala.concurrent.{ExecutionContext, Future}
import TwirlMarshaller._

class Routes(users: Users, products: Products, carts: Carts)
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

  //à modifier
  def myUserPassAuthenticator(credentials: Credentials): Option[String] =
    credentials match {
      case p @ Credentials.Provided(id) if p.verify("p4ssw0rd") => Some(id)
      case _ => None
    }

  val routes: Route =
    concat(
      path("format.css") {
        logger.info("I got a request for css resource.")
        getFromResource("stylesheets/format.css")
      },
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
        authenticateBasic(realm = "secure site", myUserPassAuthenticator) { userName =>
          (post & formFieldMap) { fields =>
            complete(super[ProductRoutes].addProduct(products, fields))
          }
        }
      },
      path("purchase") {
        (post & formFieldMap) { fields =>
          complete(super[ProductRoutes].buyProduct(products, fields))
        }
      },
      /* TODO implement when ProductRoutes.getUserCarts() is implemented
      path("carts") {
        get {
          complete(super[ProductRoutes].getUserCarts(carts))
        }
      }
       */
      path("auth") {
        (post & formFieldMap) { fields =>
          val username = fields.get("username").orNull
          val password = fields.get("password").orNull
          val exist: Future[Option[User]] = users.getUserByUsername(username)
          val ans = exist.map(_.isDefined)
          if (ans.isCompleted){
            val validCredentials = BasicHttpCredentials(username, password)
            Get("/products").addCredentials(validCredentials)
            complete(super[ProductRoutes].getProducts(products))  
          } else {
            complete(super[UserRoutes].getSignIn(users))
          }
        }
      },
    )

}
package poca

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMessage,
  HttpResponse,
  StatusCodes
}
import akka.http.scaladsl.server.Directives.{
  authenticateBasic,
  complete,
  concat,
  formFieldMap,
  get,
  path,
  post
}
import akka.http.scaladsl.server.directives.{Credentials, OnSuccessMagnet}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.{
  AuthorizationFailedRejection,
  Route,
  StandardRoute
}
import com.softwaremill.session._

import scala.concurrent.{Await, ExecutionContext, Future}
import TwirlMarshaller._
import akka.http.scaladsl.model.StatusCodes.{Found, Unauthorized}
import auth.PocaSession
import com.softwaremill.session.CsrfDirectives.{
  randomTokenCsrfProtection,
  setNewCsrfToken
}
import com.softwaremill.session.CsrfOptions.checkHeader
import com.softwaremill.session.SessionDirectives.{
  invalidateSession,
  requiredSession,
  setSession,
  optionalSession
}
import com.softwaremill.session.SessionOptions.{
  oneOff,
  refreshable,
  usingCookies,
  usingHeaders
}

import scala.concurrent.duration.{Duration, MILLISECONDS}

class Routes(users: Users, products: Products, carts: Carts, commands: Commands)
    extends UserRoutes
    with ProductRoutes
    with CommandRoutes
    with CartRoutes {

  override implicit val executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  val sessionConfig = SessionConfig.default(
    "p4NuwW0gCMGuwIVY1475AyIaZGVpFlMAmdtuErxa0Bjn6RDmidwZdv8ufohX3Z6L"
  )
  implicit val sessionManager = new SessionManager[PocaSession](sessionConfig)
  implicit val refreshTokenStorage =
    new InMemoryRefreshTokenStorage[PocaSession] {
      def log(msg: String) = logger.info(msg)
    }

  def mySetSession(v: PocaSession) = setSession(refreshable, usingCookies, v)

  val myRequiredSession = requiredSession(refreshable, usingCookies)
  val myInvalidateSession = invalidateSession(refreshable, usingCookies)
  val myOptionalSession = optionalSession(refreshable, usingCookies)


  def checkCredentials(credentials: BasicHttpCredentials) = {
    val existingUserFuture = users.getUserByUsername(credentials.username)
    existingUserFuture.map { user =>
      {
        if (user.isDefined && user.last.userPassword == credentials.password)
          user
        else
          None
      }
    }
  }

  val routes: Route =
    concat(
      path("") {
        redirect("products", Found)
      },
      path("format.css") {
        logger.info("I got a request for css resource.")
        getFromResource("stylesheets/format.css")
      },
      path("assets/*file") {
        logger.info("I got a request for css resource.")
        getFromResource("stylesheets/format.css")
      },
      path("webjar/*file") {
        logger.info("I got a request for css resource.")
        getFromResource("stylesheets/format.css")
      },
      path("signin") {
        get {
          complete(super[UserRoutes].getSignIn(users))
        }
      },
      path("auth") {
        (post & formFieldMap) { fields =>
          val username = fields.get("login").last.toString
          val password = fields.get("password").last
          val credentials = (BasicHttpCredentials(username, password))
          onSuccess(checkCredentials(credentials)) {
            case None => reject(AuthorizationFailedRejection)
            case Some(user) => {
              val session = PocaSession(user.username)
              setSession(oneOff, usingCookies, session) {
                redirect("products", Found)
              }
            }
          }
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
          myOptionalSession { session =>
            complete(super[UserRoutes].getUsers(users))
          }
        }
      },
      path("products") {
        myOptionalSession { session =>
          (post & formFieldMap) { fields =>
            complete(super[ProductRoutes].addProduct(products, fields))
          }
        }
      },
      path("products") {
        get {
          myOptionalSession { session =>
            complete(super[ProductRoutes].getProducts(products, session))
          }
        }
      },
      path("purchase") {
        myOptionalSession { session =>
          (post & formFieldMap) { fields =>
            complete(super[ProductRoutes].buyProduct(products, fields))
          }
        }
      },
      path("addProduct") {
        get {
          myOptionalSession { session =>
            complete(super[ProductRoutes].getAddProduct(products))
          }
        }
      },
      path("commands") {
        myOptionalSession { session =>
          (post & formFieldMap) { fields =>
            complete(super[CommandRoutes].addCommand(commands, fields))
          }
        }
      },
      path("commands") {
        myOptionalSession { session =>
          (get & formFieldMap) { fields =>
            complete(super[CommandRoutes].getCommand(commands, fields))
          }
        }
      },
      pathPrefix("cart") {
        concat(
          (post & formFieldMap) { fields =>
            logger.info("I got a request for /cart")
            myRequiredSession { session =>
              complete(
                super[CartRoutes]
                  .removeProductFromCart(users, carts, session.username, fields)
              )
            }
          },
          get {
            myRequiredSession { session =>
              complete(
                super[CartRoutes].getUserCarts(users, carts, session.username)
              )
            }
          }
        )
      },
      path(Remaining) { _ =>
        redirect("products", Found)
      }
    )

}

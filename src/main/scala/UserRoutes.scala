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

trait UserRoutes extends LazyLogging {

  implicit val executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  def getUsers(users: Users): Future[HtmlFormat.Appendable] = {
    logger.info("I got a request to get user list.")

    val userSeqFuture: Future[Seq[User]] = users.getAllUsers

    userSeqFuture.map(userSeq => html.users(userSeq))
  }

  def getSignIn(users: Users): HtmlFormat.Appendable = {
    logger.info("I got a request for signin.")
    html.signin()
  }

  def getSignup(users: Users): HtmlFormat.Appendable = {
    logger.info("I got a request for signup.")
    html.signup()
  }

  def register(
      users: Users,
      fields: Map[String, String]
  ): Future[HttpResponse] = {
    logger.info("I got a request to register.")
    

    fields.get("username") match {
      case Some(username) => {
        var role: Option[Role] = None
        fields.get("roleName") match {
          case Some(roleName) => {
            role = Some(
              new Role(
                roleId = None,
                roleName = roleName
              )
            )
          }
          case None => {
            role = None
          }
        }
        
        var CanCreate : Boolean = false
        fields.get("userPassword") match {
          case Some(userPassword) =>{
            fields.get("confirmPassword") match {
              case Some(confirmPassword) =>{
                if(userPassword == confirmPassword){
                  CanCreate = true
                }else{
                  Future(
                    HttpResponse(
                    StatusCodes.BadRequest,
                    entity = "Field 'pasword' and 'confirm' haven't the same value."
                    )
                  )
                }
              }
              case None => {
                Future(
                    HttpResponse(
                    StatusCodes.BadRequest,
                    entity = "You forgot to confirme your password"
                    )
                  )
              }
            }
          }
          case None => {
            Future(
                    HttpResponse(
                    StatusCodes.BadRequest,
                    entity = "You forgot to create a password"
                    )
                  )
          }
        }

        if(CanCreate){
          val userCreation = users.createUser(username = username, userPasword = userPasword, role = role)
          userCreation
            .map(_ => {
              HttpResponse(
                StatusCodes.OK,
                entity =
                  s"Welcome '$username'! You've just been registered to our great marketplace."
              )
            })
            .recover({
              case exc: UserAlreadyExistsException => {
                HttpResponse(
                  StatusCodes.OK,
                  entity =
                    s"The username '$username' is already taken. Please choose another username."
                )
              }
              case exc: RoleDoesntExistsException => {
                HttpResponse(
                  StatusCodes.OK,
                  entity = "Cannot insert user, there is not this role."
                )
              }
            })
        }
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

}

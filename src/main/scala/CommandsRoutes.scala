package poca

import akka.http.scaladsl.model.{
  ContentType,
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

trait CommandRoutes extends LazyLogging {

  implicit val executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  def addCommand(commands: Commands, fields: Map[String, String]) = {
    logger.info("I got a request to add a Command.")

    fields.get("commandName") match {
      case Some(commandName) => {
        fields.get("userId") match {
          case Some(userId) => {
            val commandCreation = commands.createCommand(
              commandName = commandName,
              userId = userId.toInt
            )
            commandCreation.map(_ => {
              HttpResponse(
                StatusCodes.OK,
                entity = "Command successfully added."
              )
            })
          } 
          case None => {
            Future(
              HttpResponse(
                StatusCodes.BadRequest,
                entity = "Field 'userId' not provided."
              )
            )
          }
        }
      }
      case None => {
        Future(
          HttpResponse(
            StatusCodes.BadRequest,
            entity = "Field 'commandName' not provided."
          )
        )
      }
    }
  }

  def getCommand(commands: Commands, fields: Map[String, String]): Future[HtmlFormat.Appendable] = {
    logger.info("I got a request to get command list.")

    fields.get("userId") match {
      case Some(userId) => {
        val commandSeqFuture: Future[Seq[Command]] = commands.getCommandByUser(userId = userId.toInt)
        commandSeqFuture.map(commandSeq => html.commands(commandSeq))    
      }
      case None => {
        val commandSeqFuture: Future[Seq[Command]] = commands.getAllCommand
        commandSeqFuture.map(commandSeq => html.commands(commandSeq))
      }
    }
  }
}
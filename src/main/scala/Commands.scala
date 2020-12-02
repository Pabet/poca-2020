package poca

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import slick.jdbc.PostgresProfile.api._

case class Command(commandId: Option[Int],
                   commandName: String,
                   commandDate: LocalDateTime,
                   userId: Int)

class CommandsTable(tag: Tag) extends Table[(Command)](tag, "commands") {
    def commandId = column[Int]("commandId", O.PrimaryKey, O.AutoInc)
    def commandName = column[String]("commandName")
    def commandDate = column[LocalDateTime]("commandDate")
    def userId = column[Int]("userId")
    def * = (commandId.?,commandName,commandDate,userId) <> (Command.tupled,Command.unapply)
}

class Commands {
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    val db = MyDatabase.db
    val commands = TableQuery[CommandsTable]

    def createCommand(commandName: String, userId: Int) = {
        val newCommand = Command(None, commandName, LocalDateTime.now(), userId)
        val dbio: DBIO[Int] = commands += newCommand
        var resultFuture: Future[Int] = db.run(dbio)
        resultFuture.map(_ => ())
    }

    def getCommandByUser(userId: Int): Future[Seq[Command]] = {
        val query = commands filter(_.userId === userId)
        val commandListFuture = db.run(query.result)
        commandListFuture.map((productList) => {
            productList
        })
    }

    def getAllCommand: Future[Seq[Command]] = {
        val commandListFuture = db.run(commands.result)
        commandListFuture.map((commandList) => {
            commandList
        })
    }
}

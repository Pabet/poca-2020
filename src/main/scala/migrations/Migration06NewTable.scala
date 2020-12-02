package migrations

import poca.{CommandsTable, Migration, MyDatabase}

import com.typesafe.scalalogging.LazyLogging
import java.time.LocalDateTime
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

class Migration06NewTable (db: Database) extends Migration with LazyLogging {

  override def apply(): Unit = {
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    val commands = TableQuery[CommandsTable]
    val dbio = DBIO.seq(commands.schema.create)
    var resultFuture = db.run(dbio)
    Await.result(resultFuture, Duration.Inf)
    logger.info("Done creating Commands")
  }
}

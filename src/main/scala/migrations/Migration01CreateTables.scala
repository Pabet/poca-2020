
package poca

import java.time.LocalDateTime

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._


class Migration01CreateTables(db: Database) extends Migration with LazyLogging {

    override def apply(): Unit = {
        implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
        val users = TableQuery[UsersTable]
        val products = TableQuery[ProductsTable]
        val dbio: DBIO[Unit] = DBIO.seq(
            users.schema.create,
            products.schema.create
        )
        val creationFuture: Future[Unit] = db.run(dbio)
        Await.result(creationFuture, Duration.Inf)
        logger.info("Done creating table Users and Products")
    }
}
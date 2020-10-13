package migrations

import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import poca.{Migration, MyDatabase, Product, ProductsTable, User, UsersTable}
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class Migration02AddFakeData(db: Database) extends Migration with LazyLogging{

  override def apply(): Unit = {
    implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    val users = TableQuery[UsersTable]
    val products = TableQuery[ProductsTable]
    val dbio = DBIO.seq(
      users += User("0","Pierre","superMdp","mail@test.com",LocalDateTime.now()),
      users += User("1","Paul","superMdp","mail@test.com",LocalDateTime.now()),
      users += User("2","Jacques","superMdp","mail@test.com",LocalDateTime.now()),
      products += Product("0","Chantilly",232.40,"La chantilly des vrais champions (recommandé par l'OMS)"),
      products += Product("1","Voiture de sport",23.40,"Une voiture pour faire du sport, elle fonctionne pas va prendre ton vélo"),
      products += Product("2","Vélo",657.40,"Un truc avec une scelle, deux pédales et 2 roues"),
      products += Product("3","Aspirateur",99.99,"Un truc qui aspire d'autres trucs. Fonctionne avec des sacs"),
      products += Product("4","Masque anti Covid-19",2.33,"Certifié AFNOR, juré le virus il passe pas")
    )
    var resultFuture = db.run(dbio)
    // We do not care about the Int value
    Await.result(resultFuture, Duration.Inf)
    logger.info("Done populating users and products with fake data")
  }
}

package poca

import slick.jdbc.PostgresProfile.api._
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

case class Category(categoryId: Option[Int], categoryName: String)

final case class CategoryDoesntExistsException(private val message: String="", private val cause: Throwable=None.orNull)
  extends Exception(message, cause)

class CategoriesTable(tag: Tag) extends Table[(Category)](tag, "categories") {
  def categoryId = column[Int]("categoryId", O.PrimaryKey, O.AutoInc)
  def categoryName = column[String]("categoryName")
  def * = (categoryId.?,categoryName) <> (Category.tupled,Category.unapply)
}

class Categories{
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val db = MyDatabase.db
  val categories = TableQuery[CategoriesTable]

  def createCategory(categoryName: String): Future[Unit] = {
    val newCategory = Category(None,categoryName)
    val dbio: DBIO[Int] = categories += newCategory
    var resultFuture: Future[Int] = db.run(dbio)
    resultFuture.map(_ => ())
  }

  def getAllCategories: Future[Seq[Category]] = {
    val categoryListFuture =  db.run(categories.result)
    categoryListFuture.map((categoryList) => {
      categoryList
    })
  }

  def getCategoryByName(categoryName: String): Future[Option[Category]] = {
    val query = categories.filter(_.categoryName === categoryName)

    val categoryListFuture = db.run(query.result)

    categoryListFuture.map((categoryList) => {
      categoryList.length match {
        case 0 => None
        case 1 => Some(categoryList.head)
        case _ => throw new InconsistentStateException(s"Category name $categoryName is linked to several categories in database!")
      }
    })
  }
}
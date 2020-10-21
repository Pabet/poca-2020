package poca

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._
import java.util.UUID
import slick.jdbc.PostgresProfile.api._

case class Product(productId: String,
                    productName: String,
                    productPrice: Double,
                    productDetail: String,
                    categoryId: Option[Int])

case class ProductWithCategory(product: Product, category: Category)

final case class IncorrectPriceException(private val message: String = "", private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

class ProductsTable(tag: Tag) extends Table[Product](tag, "products") {
  def productId = column[String]("productId", O.PrimaryKey)
  def productName = column[String]("productName")
  def productPrice = column[Double]("productPrice")
  def productDetail = column[String]("productDetail")
  def categoryId = column[Option[Int]]("productCategoryId")
  def * = (productId, productName, productPrice, productDetail,categoryId) <> (Product.tupled,Product.unapply)
  def category = foreignKey("category",categoryId,TableQuery[CategoriesTable])(_.categoryId)
}

class Products {
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val db = MyDatabase.db
  val products = TableQuery[ProductsTable]
  val categoriesTable = TableQuery[CategoriesTable]

  def createProduct(productName: String, productPrice: Double, productDetail: String, category: Option[Category]): Future[Unit] = {
    if(productPrice < 0) {
      throw new IncorrectPriceException(s"Cannot insert product '$productName' with a price < 0 : $productPrice")
    }

    val productId = UUID.randomUUID.toString

    // Future that will persist the product after the Category check
    def persistNewProduct(categoryId : Option[Int]) = {
      val newProduct = Product(productId=productId, productName=productName, productPrice=productPrice, productDetail=productDetail, categoryId = categoryId)
      val dbio: DBIO[Int] = products += newProduct
      var resultFuture: Future[Int] = db.run(dbio)
      resultFuture.map(_ => ())
    }

    // If product is created with a category, check that the category exists
    if(!category.isEmpty) {
      val categories : Categories = new Categories()
      val categoryName = category.last.categoryName
      val existingCategoryFuture = categories.getCategoryByName(category.last.categoryName)
      existingCategoryFuture.flatMap(
        existingCategory => {
          if (existingCategory.isEmpty) {
            throw new CategoryDoesntExistsException(s"There is no category named '$categoryName'.")
          } else {
            // We create the product linked to the found category
            persistNewProduct(existingCategory.last.categoryId)
          }
        }
      )
    } else {
      // The product isn't linked to a category so we can proceed without checking
      persistNewProduct(None)
    }
  }

  // Get all products
  def getAllProducts: Future[Seq[Product]] = {
    val productListFuture =  db.run(products.result)
    productListFuture.map((productList) => {
      productList
    })
  }

  // Get the tuple (Product,Category) from productId
  def getProductWithCategory(productId : String) ={
    val tupledJoin = products filter(_.productId===productId) join categoriesTable on (_.categoryId === _.categoryId)
    db.run(tupledJoin.result).map(_.map(ProductWithCategory.tupled))
  }
}
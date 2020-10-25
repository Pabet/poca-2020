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

trait ProductRoutes extends LazyLogging {

  implicit val executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  def addProduct(
      products: Products,
      fields: Map[String, String]
  ) = {
    logger.info("I got a request to add a Product.")

    var category: Option[Category] = None
    fields.get("categoryName") match {
      case Some(categoryName) => {
        category = Some(
          new Category(
            categoryId = None,
            categoryName = categoryName
          )
        )
      }
      case None => {
        category = None
      }
    }

    val productCreation = products.createProduct(
      productName = fields.get("name").orNull,
      productPrice = fields.get("price").map(f => f.toDouble).get,
      productDetail = fields.get("detail").orNull,
      category = category
    )
    productCreation
      .map(productId => {
        HttpResponse(
          StatusCodes.OK,
          entity = "Product successfully added to the marketplace."
        )
      })
      .recover({
        case exc: CategoryDoesntExistsException => {
          HttpResponse(
            StatusCodes.OK,
            entity = "Cannot insert product, there is not this category."
          )
        }
        case exc: IncorrectPriceException => {
          HttpResponse(
            StatusCodes.OK,
            entity = "Cannot insert product with a price < 0"
          )
        }
      })
  }

  def getProducts(products: Products): Future[HtmlFormat.Appendable] = {
    logger.info("I got a request to get product list.")

    val productSeqFuture: Future[Seq[Product]] = products.getAllProducts

    productSeqFuture.map(productSeq => html.products(productSeq))
  }

  def buyProduct(
      products: Products,
      fields: Map[String, String]
  ): Future[HtmlFormat.Appendable] = {
    val productId = fields.get("id").orNull
    logger.info(s"I got a request to buy the product $productId");

    val productFuture: Future[Product] = Future {
      Product(
        productId = fields.get("id").orNull,
        productName = fields.get("name").orNull,
        productPrice = fields.get("price").map(f => f.toDouble).get,
        productDetail = fields.get("detail").orNull,
        categoryId = None
      )
    }

    Future(
      HttpResponse(
        StatusCodes.OK,
        entity = s"Thank you ! you bought '$productId'"
      )
    )

    productFuture.map(product => html.purchase(product))
  }

}

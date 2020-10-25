

import scala.util.{Failure, Success}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.meta._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers}
import org.scalatest.funsuite.AnyFunSuite
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory
import poca.{Cart, Carts, Categories, Category, CategoryDoesntExistsException, MyDatabase, Product, Products, Routes, RunMigrations, User, UserAlreadyExistsException, UserDoesntExistException, Users}


class DatabaseTest extends AnyFunSuite with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {
    val rootLogger: Logger = LoggerFactory.getLogger("com").asInstanceOf[Logger]
    rootLogger.setLevel(Level.INFO)
    val slickLogger: Logger = LoggerFactory.getLogger("slick").asInstanceOf[Logger]
    slickLogger.setLevel(Level.INFO)

    // In principle, mutable objets should not be shared between tests, because tests should be independent from each other. However for performance the connection to the database should not be recreated for each test. Here we prefer to share the database.
    override def beforeAll() {
        val isRunningOnCI = sys.env.getOrElse("CI", "") != ""
        val configName = if (isRunningOnCI) "myTestDBforCI" else "myTestDB"
        val config = ConfigFactory.load().getConfig(configName)
        MyDatabase.initialize(config)
    }
    override def afterAll() {
        MyDatabase.db.close
    }

    override def beforeEach() {
        val resetSchema = sqlu"drop schema public cascade; create schema public;"
        val resetFuture: Future[Int] = MyDatabase.db.run(resetSchema)
        Await.result(resetFuture, Duration.Inf)
        new RunMigrations(MyDatabase.db)()
    }

    test("Users.createUser should create a new user") {
        val users: Users = new Users()
        val initialUsersFuture = users.getAllUsers
        var initialAllUsers = Await.result(initialUsersFuture, Duration.Inf)
        val createUserFuture = users.createUser("toto")
        val newUserId = Await.result(createUserFuture, Duration.Inf)

        val getUsersFuture: Future[Seq[User]] = users.getAllUsers
        var allUsers: Seq[User] = Await.result(getUsersFuture, Duration.Inf)

        allUsers.length should be(initialAllUsers.length+1)
        allUsers.last.username should be("toto")
        allUsers.last.userId should be(newUserId)
    }

    test("Users.createUser returned future should fail if the user already exists") {
        val users: Users = new Users()

        val createUserFuture = users.createUser("toto")
        Await.ready(createUserFuture, Duration.Inf)

        val createDuplicateUserFuture = users.createUser("toto")
        Await.ready(createDuplicateUserFuture, Duration.Inf)

        createDuplicateUserFuture.value match {
            case Some(Failure(exc: UserAlreadyExistsException)) => {
                exc.getMessage should equal ("A user with username 'toto' already exists.")
            }
            case _ => fail("The future should fail.")
        }
    }

    test("Users.getUserByUsername should return no user if it does not exist") {
        val users: Users = new Users()

        val createUserFuture = users.createUser("toto")
        Await.ready(createUserFuture, Duration.Inf)

        val returnedUserFuture: Future[Option[User]] = users.getUserByUsername("somebody-else")
        val returnedUser: Option[User] = Await.result(returnedUserFuture, Duration.Inf)

        returnedUser should be(None)
    }

    test("Users.getUserByUsername should return a user") {
        val users: Users = new Users()

        val createUserFuture = users.createUser("toto")
        Await.ready(createUserFuture, Duration.Inf)

        val returnedUserFuture: Future[Option[User]] = users.getUserByUsername("toto")
        val returnedUser: Option[User] = Await.result(returnedUserFuture, Duration.Inf)

        returnedUser match {
            case Some(user) => user.username should be("toto")
            case None => fail("Should return a user.")
        }
    }

    test("Users.getAllUsers should return a list of users") {
        val users: Users = new Users()

        val initialUsersFuture = users.getAllUsers
        var initialAllUsers = Await.result(initialUsersFuture, Duration.Inf)

        val createUserFuture = users.createUser("riri")
        Await.ready(createUserFuture, Duration.Inf)

        val createAnotherUserFuture = users.createUser("fifi")
        Await.ready(createAnotherUserFuture, Duration.Inf)

        val returnedUserSeqFuture: Future[Seq[User]] = users.getAllUsers
        val returnedUserSeq: Seq[User] = Await.result(returnedUserSeqFuture, Duration.Inf)

        returnedUserSeq.length should be(initialAllUsers.length + 2)
    }

    test("Products.createProduct should create a new product") {
        val products: Products = new Products()
        val initialProductsFuture = products.getAllProducts
        var initialAllProducts = Await.result(initialProductsFuture, Duration.Inf)
        val createProductFuture = products.createProduct("toto",210.0,"details",None)
        val newProductId = Await.result(createProductFuture, Duration.Inf)

        val getProductsFuture: Future[Seq[Product]] = products.getAllProducts
        var allProducts: Seq[Product] = Await.result(getProductsFuture, Duration.Inf)

        allProducts.length should be(initialAllProducts.length+1)
        allProducts.last.productId should be(newProductId)
        allProducts.last.productName should be("toto")
        allProducts.last.productPrice should be(210.0)
        allProducts.last.productDetail should be("details")
        allProducts.last.categoryId should be(None)
    }

    test("Products.createProduct with a category should create a new product with the id of the category") {
        val products: Products = new Products()
        val initialProductsFuture = products.getAllProducts
        var initialAllProducts = Await.result(initialProductsFuture, Duration.Inf)
        val categories : Categories = new Categories()
        val createCategoryFuture = categories.createCategory("awesomeCategory")
        Await.ready(createCategoryFuture, Duration.Inf)
        val getCategoryFuture = categories.getCategoryByName("awesomeCategory")
        val returnedCategory = Await.result(getCategoryFuture,Duration.Inf)

        val createProductFuture = products.createProduct("toto",210.0,"details",Some(Category(None,"awesomeCategory")))
        val newProductId = Await.result(createProductFuture, Duration.Inf)

        val getProductsFuture: Future[Seq[Product]] = products.getAllProducts
        var allProducts: Seq[Product] = Await.result(getProductsFuture, Duration.Inf)

        allProducts.length should be(initialAllProducts.length+1)
        allProducts.last.productId should be(newProductId)
        allProducts.last.productName should be("toto")
        allProducts.last.productPrice should be(210.0)
        allProducts.last.productDetail should be("details")
        allProducts.last.categoryId should be(returnedCategory.last.categoryId)
    }

    test("Products.createProduct should fail if category doesn't exist") {
        val products: Products = new Products()
        val initialProductsFuture = products.getAllProducts
        var initialAllProducts = Await.result(initialProductsFuture, Duration.Inf)
        val createProductFuture = products.createProduct("toto",210.0,"details",Some(Category(None,"TYUIKDF")))
        Await.ready(createProductFuture, Duration.Inf)

        createProductFuture.value match {
            case Some(Failure(exc: CategoryDoesntExistsException)) => {
                exc.getMessage should equal ("There is no category named 'TYUIKDF'.")
            }
            case _ => fail("The future should fail.")
        }
    }

    test("Products.getAllProducts should return a list of products") {
        val products: Products = new Products()

        val initialProductsFuture = products.getAllProducts
        var initialAllProducts = Await.result(initialProductsFuture, Duration.Inf)

        val createProductFuture = products.createProduct("Chaise",300.20,"Chaise confortable pour salle à manger",None)
        Await.ready(createProductFuture, Duration.Inf)

        val createAnotherProductFuture = products.createProduct("Miroir",100.10,"Voir le visage",None)
        Await.ready(createAnotherProductFuture, Duration.Inf)

        val returnedProductSeqFuture: Future[Seq[Product]] = products.getAllProducts
        val returnedProductSeq: Seq[Product] = Await.result(returnedProductSeqFuture, Duration.Inf)

        returnedProductSeq.length should be(initialAllProducts.length + 2)
    }

    test("Products.getProductWithCategory should return a tuple (Product,Category)") {
        val products: Products = new Products()

        val categories : Categories = new Categories()
        val createCategoryFuture = categories.createCategory("awesomeCategory")
        Await.ready(createCategoryFuture, Duration.Inf)

        val createProductFuture = products.createProduct("Chaise",300.20,"Chaise confortable pour salle à manger",Some(Category(None,"awesomeCategory")))
        Await.ready(createProductFuture, Duration.Inf)

        val returnedProductSeqFuture: Future[Seq[Product]] = products.getAllProducts
        val returnedProductSeq: Seq[Product] = Await.result(returnedProductSeqFuture, Duration.Inf)

        val productWithCategory = Await.result(products.getProductWithCategory(returnedProductSeq.last.productId),Duration.Inf)
        productWithCategory.last.category.categoryName should be("awesomeCategory")
        productWithCategory.last.product.productName should be("Chaise")
        productWithCategory.last.product.productPrice should be(300.20)
        productWithCategory.last.product.productDetail should be("Chaise confortable pour salle à manger")
    }

    test("Categories.createCategory should create a new category") {
        val categories: Categories = new Categories()
        val initialCategoriesFuture = categories.getAllCategories
        var initialAllCategories = Await.result(initialCategoriesFuture, Duration.Inf)
        val createCategoryFuture = categories.createCategory("sample")
        Await.ready(createCategoryFuture, Duration.Inf)

        // Check that the future succeeds
        createCategoryFuture.value should be(Some(Success(())))

        val getUsersFuture: Future[Seq[Category]] = categories.getAllCategories
        var allCategories: Seq[Category] = Await.result(getUsersFuture, Duration.Inf)

        allCategories.length should be(initialAllCategories.length+1)
        allCategories.last.categoryName should be("sample")
        allCategories.last.categoryId.last should be(initialAllCategories.last.categoryId.last+1)
    }

    test("Categories.getAllCategories should return a list of categories") {
        val categories: Categories = new Categories()

        val initialCategoriesFuture = categories.getAllCategories
        var initialAllCategories = Await.result(initialCategoriesFuture, Duration.Inf)

        val createCategoryFuture = categories.createCategory("riri")
        Await.ready(createCategoryFuture, Duration.Inf)

        val createAnotherCategoryFuture = categories.createCategory("fifi")
        Await.ready(createAnotherCategoryFuture, Duration.Inf)

        val returnedCategorySeqFuture: Future[Seq[Category]] = categories.getAllCategories
        val returnedCategorySeq: Seq[Category] = Await.result(returnedCategorySeqFuture, Duration.Inf)

        returnedCategorySeq.length should be(initialAllCategories.length + 2)
    }

    test("Categories.getCategoryByName should return no category if it does not exist") {
        val categories: Categories = new Categories()

        val createCategoryFuture = categories.createCategory("toto")
        Await.ready(createCategoryFuture, Duration.Inf)

        val returnedCategoryFuture: Future[Option[Category]] = categories.getCategoryByName("somebody-else")
        val returnedCategory: Option[Category] = Await.result(returnedCategoryFuture, Duration.Inf)

        returnedCategory should be(None)
    }

    test("Categories.getCategoryByName should return a category") {
        val categories: Categories = new Categories()

        val createCategoryFuture = categories.createCategory("toto")
        Await.ready(createCategoryFuture, Duration.Inf)

        val returnedCategoryFuture: Future[Option[Category]] = categories.getCategoryByName("toto")
        val returnedCategory: Option[Category] = Await.result(returnedCategoryFuture, Duration.Inf)

        returnedCategory match {
            case Some(category) => category.categoryName should be("toto")
            case None => fail("Should return a user.")
        }
    }

    test("Carts.createCart should fail if related user doesn't exist") {
        val carts = new Carts()
        val createCartFuture = carts.createCart("INCORRECTID")
        Await.ready(createCartFuture, Duration.Inf)
        createCartFuture.value match {
            case Some(Failure(exc: UserDoesntExistException)) => {
                exc.getMessage should equal ("Cannot create cart, there is no user with ID 'INCORRECTID'.")
            }
            case _ => fail("The future should fail.")
        }
    }

    test("Carts.createCart should create a new cart linked to existing user") {
        val carts = new Carts()
        val users = new Users()

        val initialAllCartFuture = carts.getAllCarts
        var initialAllCarts: Seq[Cart] = Await.result(initialAllCartFuture, Duration.Inf)
        // Create user
        val createUserFuture = users.createUser("toto")
        Await.ready(createUserFuture, Duration.Inf)
        val getUserFuture = users.getUserByUsername("toto")
        val returnedUser = Await.result(getUserFuture,Duration.Inf)
        //Create cart
        val createCartFuture = carts.createCart(returnedUser.last.userId)
        Await.ready(createCartFuture, Duration.Inf)
        // Check that the future succeeds
        createCartFuture.value should be(Some(Success(())))

        val getAllCartFuture = carts.getAllCarts
        var allCarts: Seq[Cart] = Await.result(getAllCartFuture, Duration.Inf)

        allCarts.length should be(initialAllCarts.length+1)
        allCarts.last.userId should be(returnedUser.last.userId)
    }

    test("Carts.getCartWithProducts should return a cart with a seq of products") {
        val carts = new Carts()
        val users = new Users()
        val products = new Products()

        // Create user
        val createUserFuture = users.createUser("toto")
        val newUserId = Await.result(createUserFuture, Duration.Inf)
        // Create cart
        val createCartFuture = carts.createCart(newUserId)
        Await.ready(createCartFuture, Duration.Inf)
        val getAllCartsFuture = carts.getAllCarts
        val allCarts = Await.result(getAllCartsFuture,Duration.Inf)
        val newCartId = allCarts.last.cartId.last
        // Create product
        val createProductFuture = products.createProduct("Chaise",300.20,"Chaise confortable pour salle à manger",None)
        val newProductId = Await.result(createProductFuture, Duration.Inf)
        // Add product to cart
        val addProductFuture = carts.addProductToCart(cartId = newCartId, productId = newProductId, 2)

        val getCartWithProductFuture = carts.getCartWithProducts(newCartId)
        val cartWithProduct = Await.result(getCartWithProductFuture,Duration.Inf)
        cartWithProduct.cart.cartId.last should be(newCartId)
        cartWithProduct.products.last.product.productId should be(newProductId)
        cartWithProduct.products.last.product.productName should be("Chaise")
        cartWithProduct.products.last.quantity should be(2)
    }

}

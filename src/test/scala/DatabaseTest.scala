import java.util.UUID

import ch.qos.logback.classic.{Level, Logger}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers}
import org.slf4j.LoggerFactory
import poca.MyDatabase.executionContext
import poca._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}


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
        val createUserFuture = users.createUser("toto", "1234", None)
        val newUserId = Await.result(createUserFuture, Duration.Inf)

        val getUsersFuture: Future[Seq[User]] = users.getAllUsers
        var allUsers: Seq[User] = Await.result(getUsersFuture, Duration.Inf)

        allUsers.length should be(initialAllUsers.length+1)
        allUsers.last.username should be("toto")
        allUsers.last.userId should be(newUserId)
        allUsers.last.roleId should be(None)
    }

    test("Users.createUser should create a new user with the id of the role") {
        val users: Users = new Users()
        val initialUsersFuture = users.getAllUsers
        var initialAllUsers = Await.result(initialUsersFuture, Duration.Inf)

        val roles: Roles = new Roles()
        val createRoleFuture = roles.createRole("awesomeRole")

        val createUserFuture = users.createUser("toto", "1234", Some(Role(None, "awesomeRole")))
        val newUserId = Await.result(createUserFuture, Duration.Inf)

        val getRoleFuture = roles.getRoleByName("awesomeRole")
        val returnedRole = Await.result(getRoleFuture, Duration.Inf)

        val getUsersFuture: Future[Seq[User]] = users.getAllUsers
        var allUsers: Seq[User] = Await.result(getUsersFuture, Duration.Inf)

        allUsers.length should be(initialAllUsers.length+1)
        allUsers.last.username should be("toto")
        allUsers.last.userId should be(newUserId)
        allUsers.last.roleId should be(returnedRole.last.roleId)
    }

    test("Users.createUser returned future should fail if the user already exists") {
        val users: Users = new Users()

        val createUserFuture = users.createUser("toto", "1234", None)
        Await.ready(createUserFuture, Duration.Inf)

        val createDuplicateUserFuture = users.createUser("toto", "1234", None)
        Await.ready(createDuplicateUserFuture, Duration.Inf)

        createDuplicateUserFuture.value match {
            case Some(Failure(exc: UserAlreadyExistsException)) => {
                exc.getMessage should equal ("A user with username 'toto' already exists.")
            }
            case _ => fail("The future should fail.")
        }
    }

    test("Users.createUser returned future should fail if the role does not exists") {
        val users: Users = new Users()

        val createUserFuture = users.createUser("toto", "1234", Some(Role(None, "DoesNotExists")))
        Await.ready(createUserFuture, Duration.Inf)

        createUserFuture.value match {
            case Some(Failure(exc: RoleDoesntExistsException)) => {
                exc.getMessage should equal ("There is no role named 'DoesNotExists'.")
            }
            case _ => fail("The future should fail.")
        }
    }

    test("Users.getUserByUsername should return no user if it does not exist") {
        val users: Users = new Users()

        val createUserFuture = users.createUser("toto", "1234", None)
        Await.ready(createUserFuture, Duration.Inf)

        val returnedUserFuture: Future[Option[User]] = users.getUserByUsername("somebody-else")
        val returnedUser: Option[User] = Await.result(returnedUserFuture, Duration.Inf)

        returnedUser should be(None)
    }

    test("Users.getUserByUsername should return a user") {
        val users: Users = new Users()

        val createUserFuture = users.createUser("toto", "1234", None)
        Await.ready(createUserFuture, Duration.Inf)

        val returnedUserFuture: Future[Option[User]] = users.getUserByUsername("toto")
        val returnedUser: Option[User] = Await.result(returnedUserFuture, Duration.Inf)

        returnedUser match {
            case Some(user) => user.username should be("toto")
            case None => fail("Should return a user.")
        }
    }

    test("Users.getUserByUsernameWithRole should return a tuple (User,Role)") {
        val users: Users = new Users()

        val roles : Roles = new Roles()
        val createRoleFuture = roles.createRole("awesomeRole")
        Await.ready(createRoleFuture, Duration.Inf)

        val createUserFuture = users.createUser("toto","1234",Some(Role(None,"awesomeRole")))
        val newUserId = Await.ready(createUserFuture, Duration.Inf)

        val returnedUserSeqFuture: Future[Seq[User]] = users.getAllUsers
        val returnedUserSeq: Seq[User] = Await.result(returnedUserSeqFuture, Duration.Inf)

        val userWithRole = Await.result(users.getUserByUsernameWithRole("toto"),Duration.Inf)
        userWithRole.last.role.roleName should be("awesomeRole")
        userWithRole.last.user.username should be("toto")
    }

    test("Users.getAllUsers should return a list of users") {
        val users: Users = new Users()

        val initialUsersFuture = users.getAllUsers
        var initialAllUsers = Await.result(initialUsersFuture, Duration.Inf)

        val createUserFuture = users.createUser("riri","abcde", None)
        Await.ready(createUserFuture, Duration.Inf)

        val createAnotherUserFuture = users.createUser("fifi","edcba", None)
        Await.ready(createAnotherUserFuture, Duration.Inf)

        val returnedUserSeqFuture: Future[Seq[User]] = users.getAllUsers
        val returnedUserSeq: Seq[User] = Await.result(returnedUserSeqFuture, Duration.Inf)

        returnedUserSeq.length should be(initialAllUsers.length + 2)
    }

    test("Roles.createRole should create a new role") {
        val roles: Roles = new Roles()

        val initialRolesFuture = roles.getAllRoles
        var initialAllRoles = Await.result(initialRolesFuture, Duration.Inf)

        val createRoleFuture = roles.createRole("test")
        Await.ready(createRoleFuture, Duration.Inf)

        // Check that the future succeeds
        createRoleFuture.value should be(Some(Success(())))

        val getRolesFuture: Future[Seq[Role]] = roles.getAllRoles
        var allRoles: Seq[Role] = Await.result(getRolesFuture, Duration.Inf)

        allRoles.length should be(initialAllRoles.length+1)
        allRoles.last.roleName should be("test")
        allRoles.last.roleId.last should be(initialAllRoles.last.roleId.last+1)
    }

    test("Roles.getAllRoles should return a list of roles") {
        val roles: Roles = new Roles()

        val initialRolesFuture = roles.getAllRoles
        var initialAllRoles = Await.result(initialRolesFuture, Duration.Inf)

        val createRoleFuture = roles.createRole("test")
        Await.ready(createRoleFuture, Duration.Inf)

        val createAnotherRoleFuture = roles.createRole("test2")
        Await.ready(createAnotherRoleFuture, Duration.Inf)

        val getRolesFuture: Future[Seq[Role]] = roles.getAllRoles
        val allRoles: Seq[Role] = Await.result(getRolesFuture, Duration.Inf)

        allRoles.length should be(initialAllRoles.length + 2)
    }

    test("Roles.getRoleByName should return no role if it does not exist") {
        val roles: Roles = new Roles()

        val createRoleFuture = roles.createRole("test")
        Await.ready(createRoleFuture, Duration.Inf)

        val getRoleFuture: Future[Option[Role]] = roles.getRoleByName("DoesNotExists")
        val returnedRole: Option[Role] = Await.result(getRoleFuture, Duration.Inf)

        returnedRole should be(None)
    }

    test("Roles.getRoleByName should return a role") {
        val roles: Roles = new Roles()

        val createRoleFuture = roles.createRole("test")
        Await.ready(createRoleFuture, Duration.Inf)

        val getRoleFuture: Future[Option[Role]] = roles.getRoleByName("test")
        val returnedRole: Option[Role] = Await.result(getRoleFuture, Duration.Inf)

        returnedRole match {
            case Some(role) => role.roleName should be("test")
            case None => fail("Should return a role.")
        }
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
        val createUserFuture = users.createUser("toto", "1234", None)
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
        val createUserFuture = users.createUser("toto", "1234", None)
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
        val addProductFuture = carts.addProductToCart(cartId = newCartId, productId = newProductId, productQuantity = 2)
        Await.result(addProductFuture, Duration.Inf)

        val getCartWithProductFuture = carts.getCartWithProducts(newCartId)
        val cartWithProduct = Await.result(getCartWithProductFuture,Duration.Inf)

        cartWithProduct.cart.cartId.last should be(newCartId)
        cartWithProduct.products.last.product.productId should be(newProductId)
        cartWithProduct.products.last.product.productName should be("Chaise")
        cartWithProduct.products.last.quantity should be(2)
    }

    test("Carts.getAllCartsForUser should return only carts of specific user"){
        val carts = new Carts()
        val users = new Users()

        val userId = Await.result(users.createUser("toto", "1234", None),Duration.Inf)
        val secondUserId = Await.result(users.createUser("tata", "5678", None),Duration.Inf)

        Await.result(carts.createCart(userId), Duration.Inf)
        Await.result(carts.createCart(userId), Duration.Inf)
        val user1Carts = Await.result(carts.getAllCartsForUser(userId),Duration.Inf)
        val user2Carts = Await.result(carts.getAllCartsForUser(secondUserId),Duration.Inf)

        user1Carts.length should be(2)
        user2Carts.length should be(0)
    }

    test("Carts.getLastCartForUser should return only the last card by cart date"){
        val carts = new Carts()
        val users = new Users()

        val userId = Await.result(users.createUser("toto", "1234", None),Duration.Inf)

        carts.createCart(userId)
        carts.createCart(userId)
        val userCarts = Await.result(carts.getAllCartsForUser(userId),Duration.Inf)
        val userLastCart = Await.result(carts.getLastCartForUser(userId),Duration.Inf)

        for(cart <- userCarts){
            userLastCart.cartDate should be >= cart.cartDate
        }
    }

    test("Carts.getProductQuantityFromCart should return quantity of specified product from cart") {
        val carts = new Carts()
        val users = new Users()
        val products = new Products()

        val createUserFuture = users.createUser("toto", "1234", None)
        val newUserId = Await.result(createUserFuture, Duration.Inf)

        val createCartFuture = carts.createCart(newUserId)
        Await.ready(createCartFuture, Duration.Inf)
        val getAllCartsFuture = carts.getAllCarts
        val allCarts = Await.result(getAllCartsFuture,Duration.Inf)
        val newCartId = allCarts.last.cartId.last

        val createProductFuture = products.createProduct("Chaise",24.99,"chaise ikea",None)
        val newProductId = Await.result(createProductFuture, Duration.Inf)

        val addProductFuture = carts.addProductToCart(cartId = newCartId, productId = newProductId, 3)
        Await.ready(addProductFuture, Duration.Inf)

        val getQuantityProductFuture = carts.getProductQuantityFromCart(cartId = newCartId, productId = newProductId)
        val quantityProduct = Await.result(getQuantityProductFuture, Duration.Inf)

        quantityProduct.isEmpty should be (false)
        quantityProduct.last > 0 should be (true)
        quantityProduct.last should be (3)
    }

    test("Carts.removeProductFromCart should remove product from cart") {
        val carts = new Carts()
        val users = new Users()
        val products = new Products()

        val createUserFuture = users.createUser("toto", "1234", None)
        val newUserId = Await.result(createUserFuture, Duration.Inf)

        val createCartFuture = carts.createCart(newUserId)
        Await.ready(createCartFuture, Duration.Inf)
        val getAllCartsFuture = carts.getAllCarts
        val allCarts = Await.result(getAllCartsFuture,Duration.Inf)
        val newCartId = allCarts.last.cartId.last

        val createProductFuture = products.createProduct("Chaise",24.99,"chaise ikea",None)
        val newProductId = Await.result(createProductFuture, Duration.Inf)

        val addProductFuture = carts.addProductToCart(cartId = newCartId, productId = newProductId, productQuantity = 3)
        Await.ready(addProductFuture, Duration.Inf)

        val getCartProductsFuture = carts.getCartWithProducts(newCartId)
        Await.ready(getCartProductsFuture, Duration.Inf)

        val removedProduct = carts.removeProductFromCart(cartId = newCartId, productId = newProductId)
        Await.ready(removedProduct, Duration.Inf)

        val getNewCartProductFuture = carts.getCartWithProducts(newCartId)
        val newCartWithProduct = Await.result(getNewCartProductFuture, Duration.Inf)
        newCartWithProduct.products.last.quantity should be (2)
    }

    test("Carts.removeAllProductFromCart should return empty cart") {
        val carts = new Carts()
        val users = new Users()
        val products = new Products()

        val createUserFuture = users.createUser("toto", "1234", None)
        val newUserId = Await.result(createUserFuture, Duration.Inf)

        val createCartFuture = carts.createCart(newUserId)
        Await.ready(createCartFuture, Duration.Inf)
        val getAllCartsFuture = carts.getAllCarts
        val allCarts = Await.result(getAllCartsFuture, Duration.Inf)
        val newCartId = allCarts.last.cartId.last

        val createProductFuture = products.createProduct("Chaise", 24.99, "chaise ikea", None)
        val newProductId = Await.result(createProductFuture, Duration.Inf)

        val addProductFuture = carts.addProductToCart(cartId = newCartId, productId = newProductId, 3)
        Await.ready(addProductFuture, Duration.Inf)

        val removeAllProductFuture = carts.removeAllProductFromCart(cartId = newCartId)
        Await.ready(removeAllProductFuture, Duration.Inf)

        val getEmptyCartProductFuture = carts.getCartWithProducts(newCartId)
        val newEmptyCart = Await.result(getEmptyCartProductFuture, Duration.Inf)

        newEmptyCart.products.isEmpty should be(true)
    }

    test("Carts.cartContainsProduct should return true if $cart contains $product, false otherwise"){
        //create tables
        val carts = new Carts()
        val products = new Products()
        val users = new Users()

        //create user
        val userId = Await.result(users.createUser("testguy","testpswrd", None), Duration.Inf)
        val createCartFuture = carts.createCart(userId)
        Await.ready(createCartFuture, Duration.Inf)

        //create cart
        val getAllCartsFuture = carts.getAllCarts
        val allCarts = Await.result(getAllCartsFuture, Duration.Inf)
        val newCartId = allCarts.last.cartId.last

        //create product
        val createProductFuture = products.createProduct("Pont Neuf",1607.00,"pont, très joli",None)
        val newProductId = Await.result(createProductFuture, Duration.Inf)

        // Add product to cart
        val addProductFuture = carts.addProductToCart(cartId = newCartId, productId = newProductId, 1)
        Await.result(addProductFuture, Duration.Inf)

        val getCartWithProductFuture = carts.getCartWithProducts(newCartId)
        val cartWithProduct = Await.result(getCartWithProductFuture,Duration.Inf)

        //cart contains added product
        val cartContainsProduct = Await.result(carts.cartContainsProduct(cartWithProduct.cart.cartId.last, cartWithProduct.products.last.product.productId), Duration.Inf)
        cartContainsProduct should be (true)

        //cart doesn't contain other product
        val randomProductId = UUID.randomUUID.toString
        if (randomProductId != newProductId) {
            val cartDoesntContainRandomProduct = Await.result(carts.cartContainsProduct(cartWithProduct.cart.cartId.last, randomProductId), Duration.Inf)
            cartDoesntContainRandomProduct should be (false)
        }
    }


    test("Carts.getCartAmount should return total amount of cart") {
        val carts = new Carts()
        val users = new Users()
        val products = new Products()

        val createUserFuture = users.createUser("toto", "1234", None)
        val newUserId = Await.result(createUserFuture, Duration.Inf)

        val createCartFuture = carts.createCart(newUserId)
        Await.ready(createCartFuture, Duration.Inf)
        val getAllCartsFuture = carts.getAllCarts
        val allCarts = Await.result(getAllCartsFuture, Duration.Inf)
        val newCartId = allCarts.last.cartId.last

        val createProductFuture = products.createProduct("Chaise", 24.99, "chaise ikea", None)
        val newProductId = Await.result(createProductFuture, Duration.Inf)

        val addProductFuture = carts.addProductToCart(cartId = newCartId, productId = newProductId, 3)
        Await.ready(addProductFuture, Duration.Inf)

        val getCartAmountFuture = carts.getCartAmount(newCartId)
        val cartAmount = Await.result(getCartAmountFuture, Duration.Inf)

        cartAmount.map(_.isEmpty should be(false))
        cartAmount.map(_.last >= 0 should be(true))
        cartAmount.map(_.last should be(74.97))
    }

    test("Carts.ChangeAmountOfProductInCart changes product quantity correctly"){
        val carts = new Carts()
        val products = new Products()
        val users = new Users()

        //create user
        val userId = Await.result(users.createUser("testguy", "testpswrd", None), Duration.Inf)
        val createCartFuture = carts.createCart(userId)
        Await.ready(createCartFuture, Duration.Inf)

        //create cart
        val getAllCartsFuture = carts.getAllCarts
        val allCarts = Await.result(getAllCartsFuture,Duration.Inf)
        val newCartId = allCarts.last.cartId.last

        //create product
        val createProductFuture = products.createProduct("Pont de la Concorde",1792.00,"un autre pont",None)
        val newProductId = Await.result(createProductFuture, Duration.Inf)

        // Add product to cart
        val addProductFuture = carts.addProductToCart(cartId = newCartId, productId = newProductId, 2)
        Await.ready(addProductFuture, Duration.Inf)
        // and change the amount of product
        val changedAmount = Await.ready(carts.changeAmountOfProductInCart(cartId = newCartId, productId = newProductId, newQuantity = 3), Duration.Inf)
        //try to change amount of non existing product
        //val testFuture = carts.changeAmountOfProductInCart(cartId = 23, productId = newProductId, newQuantity = 3)

        val getCartWithProductFuture = carts.getCartWithProducts(newCartId)
        val cart = Await.result(getCartWithProductFuture, Duration.Inf)

        cart.products.last.quantity should be (3)
    }

    test("Commands.createCommand should create a new command") {
        val commands = new Commands()

        val initialCommandsFuture = commands.getAllCommand
        var initialAllCommands: Seq[Command] = Await.result(initialCommandsFuture, Duration.Inf)

        val createCommandFuture = commands.createCommand("toto", 1)
        Await.ready(createCommandFuture, Duration.Inf)

        val commandsFuture = commands.getAllCommand
        var allCommands: Seq[Command] = Await.result(commandsFuture, Duration.Inf)

        allCommands.length should be(initialAllCommands.length + 1)
        allCommands.last.commandName should be("toto")
        allCommands.last.userId should be(1)
    }

    test("Commands.getCommandByUser should return a list of commands") {
        val commands = new Commands()

        val initialCommandsFuture = commands.getCommandByUser(0)
        var initialAllCommands = Await.result(initialCommandsFuture, Duration.Inf)

        val createCommandFuture = commands.createCommand("toto", 0)
        Await.result(createCommandFuture, Duration.Inf)

        val createCommandFuture2 = commands.createCommand("titi", 1)
        Await.result(createCommandFuture2, Duration.Inf)

        val createCommandFuture3 = commands.createCommand("tata", 0)
        Await.result(createCommandFuture3, Duration.Inf)

        val commandsFuture = commands.getCommandByUser(0)
        var allCommands = Await.result(commandsFuture, Duration.Inf)

        allCommands.length should be(initialAllCommands.length+2)
    }

}

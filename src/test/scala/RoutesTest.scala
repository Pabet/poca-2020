import java.time.LocalDateTime

import scala.concurrent.Future
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.model.{ContentTypes, FormData, HttpMethods, HttpRequest, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuite
import org.scalamock.scalatest.MockFactory
import poca.{Carts, Categories, Category, CategoryDoesntExistsException, IncorrectPriceException, MyDatabase, Product, Products, Role, Roles, RoleDoesntExistsException, Routes, User, UserAlreadyExistsException, Users}
import akka.http.scaladsl.model.headers.BasicHttpCredentials

class RoutesTest extends AnyFunSuite with Matchers with MockFactory with ScalatestRouteTest {

    // the Akka HTTP route testkit does not yet support a typed actor system (https://github.com/akka/akka-http/issues/2036)
    // so we have to adapt for now
    lazy val testKit = ActorTestKit()
    implicit def typedSystem = testKit.system
    override def createActorSystem(): akka.actor.ActorSystem =
        testKit.system.classicSystem

    test("Route GET /hello should say hello") {
        var mockUsers = mock[Users]
        val mockProducts = mock[Products]
        var mockCarts = mock[Carts]

        val routesUnderTest = new Routes(mockUsers,mockProducts,mockCarts).routes

        val request = HttpRequest(uri = "/hello")
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)

            contentType should ===(ContentTypes.`text/html(UTF-8)`)

            entityAs[String] should ===("<h1>Say hello to akka-http</h1><p>A marketpace developped by Dirty Picnic Tractors</p>")
        }
    }

    test("Route GET /signup should returns the signup page") {
        var mockUsers = mock[Users]
        val mockProducts = mock[Products]
        var mockCarts = mock[Carts]
        val routesUnderTest = new Routes(mockUsers,mockProducts,mockCarts).routes
        val request = HttpRequest(uri = "/signup")
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)

            contentType should ===(ContentTypes.`text/html(UTF-8)`)

        }
    }

    test("Route POST /register should create a new user") {
        val role = Some(Role(None, "testRole"))
        var mockUsers = mock[Users]
        (mockUsers.createUser _).expects("toto", role).returning(Future("anyString"))
        val mockProducts = mock[Products]
        var mockCarts = mock[Carts]
        val routesUnderTest = new Routes(mockUsers,mockProducts,mockCarts).routes

        val request = HttpRequest(
            method = HttpMethods.POST,
            uri = "/register",
            entity = FormData(("username", "toto"), ("roleName", "testRole")).toEntity
        )
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)

            contentType should ===(ContentTypes.`text/plain(UTF-8)`)

            entityAs[String] should ===("Welcome 'toto'! You've just been registered to our great marketplace.")
        }
    }

    test("Route POST /register should warn the user when username is already taken") {
        var mockUsers = mock[Users]
        (mockUsers.createUser _).expects("toto", None).returns(Future({
            throw new UserAlreadyExistsException("")
        })).once()
        val mockProducts = mock[Products]
        var mockCarts = mock[Carts]

        val routesUnderTest = new Routes(mockUsers,mockProducts,mockCarts).routes

        val request = HttpRequest(
            method = HttpMethods.POST,
            uri = "/register",
            entity = FormData(("username", "toto")).toEntity
        )
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)

            contentType should ===(ContentTypes.`text/plain(UTF-8)`)

            entityAs[String] should ===("The username 'toto' is already taken. Please choose another username.")
        }
    }

    test("Route POST /register should warn the user when user is not added due to role") {
        var mockUsers = mock[Users]
        val role = Some(Role(None, "testRole"))
        (mockUsers.createUser _).expects("toto", role).returning(Future({
            throw new RoleDoesntExistsException("")
        })).once()

        val mockProducts = mock[Products]
        var mockCarts = mock[Carts]

        val routesUnderTest = new Routes(mockUsers,mockProducts, mockCarts).routes

        val request = HttpRequest(
            method = HttpMethods.POST,
            uri = "/register",
            entity = FormData(("username", "toto"), ("roleName", "testRole")).toEntity
        )
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)
            contentType should ===(ContentTypes.`text/plain(UTF-8)`)
            entityAs[String] should ===("Cannot insert user, there is not this role.")
        }
    }

    test("Route GET /users should display the list of users") {
        var mockUsers = mock[Users]
        var mockCarts = mock[Carts]
        val userList = List(
            User(username="riri", userId="id1",userPassword="",userMail="",userLastConnection = LocalDateTime.now, roleId = None),
            User(username="fifi", userId="id2",userPassword="",userMail="",userLastConnection = LocalDateTime.now, roleId = None),
            User(username="lulu", userId="id2",userPassword="",userMail="",userLastConnection = LocalDateTime.now, roleId = None)
        )
        (mockUsers.getAllUsers _).expects().returns(Future(userList)).once()

        val mockProducts = mock[Products]
        val routesUnderTest = new Routes(mockUsers,mockProducts,mockCarts).routes

        val request = HttpRequest(uri = "/users")
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)

            contentType should ===(ContentTypes.`text/html(UTF-8)`)
        }
    }

    test("Route POST /products should create a new product") {
        var mockUsers = mock[Users]
        val mockProducts = mock[Products]
        var mockCarts = mock[Carts]
        val category = Some(Category(None, "testCategory"))
        (mockProducts.createProduct _).expects("test", 1, "test details", category).returning(Future("anyString"))

        val routesUnderTest = new Routes(mockUsers,mockProducts,mockCarts).routes

        val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")

        val request = HttpRequest(
            method = HttpMethods.POST,
            uri = "/products",
            entity = FormData(("name", "test"), ("price", "1"), ("detail", "test details"), ("categoryName", "testCategory")).toEntity
        )
        request ~> addCredentials(validCredentials) ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)
            contentType should ===(ContentTypes.`text/plain(UTF-8)`)
            entityAs[String] should ===("Product successfully added to the marketplace.")
        }
    }

    test("Route POST /products should warn the user when product is not added due to price") {
        var mockUsers = mock[Users]
        var mockProducts = mock[Products]
        var mockCarts = mock[Carts]
        (mockProducts.createProduct _).expects("test", -1, "test details", None).returns(Future({
            throw new IncorrectPriceException("")
        })).once()

        val routesUnderTest = new Routes(mockUsers,mockProducts,mockCarts).routes

        val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")

        val request = HttpRequest(
            method = HttpMethods.POST,
            uri = "/products",
            entity = FormData(("name", "test"), ("price", "-1"), ("detail", "test details")).toEntity
        )
        request ~> addCredentials(validCredentials) ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)
            contentType should ===(ContentTypes.`text/plain(UTF-8)`)
            entityAs[String] should ===("Cannot insert product with a price < 0")
        }
    }

    test("Route POST /products should warn the user when product is not added due to category") {
        var mockUsers = mock[Users]
        val mockProducts = mock[Products]
        var mockCarts = mock[Carts]
        
        val category = Some(Category(None, "testCategory"))
        (mockProducts.createProduct _).expects("test", 1, "test details", category).returning(Future({
            throw new IncorrectPriceException("")
        })).once()

        val routesUnderTest = new Routes(mockUsers,mockProducts,mockCarts).routes

        val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")

        val request = HttpRequest(
            method = HttpMethods.POST,
            uri = "/products",
            entity = FormData(("name", "test"), ("price", "1"), ("detail", "test details"), ("categoryName", "testCategory")).toEntity
        )
        request ~> addCredentials(validCredentials) ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)
            contentType should ===(ContentTypes.`text/plain(UTF-8)`)
            entityAs[String] should ===("Cannot insert product with a price < 0")
        }
    }
}

import java.time.LocalDateTime

import scala.concurrent.Future
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.model.{ContentTypes, FormData, HttpMethods, HttpRequest, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuite
import org.scalamock.scalatest.MockFactory
import poca.{MyDatabase, Routes, User, UserAlreadyExistsException, Users, Products, Product}


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
        val routesUnderTest = new Routes(mockUsers,mockProducts).routes

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
        val routesUnderTest = new Routes(mockUsers,mockProducts).routes
        val request = HttpRequest(uri = "/signup")
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)

            contentType should ===(ContentTypes.`text/html(UTF-8)`)

            entityAs[String].length should be(330)
        }
    }

    test("Route POST /register should create a new user") {
        var mockUsers = mock[Users]
        (mockUsers.createUser _).expects("toto").returning(Future(())).once()
        val mockProducts = mock[Products]
        val routesUnderTest = new Routes(mockUsers,mockProducts).routes

        val request = HttpRequest(
            method = HttpMethods.POST,
            uri = "/register",
            entity = FormData(("username", "toto")).toEntity
        )
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)

            contentType should ===(ContentTypes.`text/plain(UTF-8)`)

            entityAs[String] should ===("Welcome 'toto'! You've just been registered to our great marketplace.")
        }
    }

    test("Route POST /register should warn the user when username is already taken") {
        var mockUsers = mock[Users]
        (mockUsers.createUser _).expects("toto").returns(Future({
            throw new UserAlreadyExistsException("")
        })).once()
        val mockProducts = mock[Products]

        val routesUnderTest = new Routes(mockUsers,mockProducts).routes

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

    test("Route GET /users should display the list of users") {
        var mockUsers = mock[Users]
        val userList = List(
            User(username="riri", userId="id1",userPassword="",userMail="",userLastConnection = LocalDateTime.now),
            User(username="fifi", userId="id2",userPassword="",userMail="",userLastConnection = LocalDateTime.now),
            User(username="lulu", userId="id2",userPassword="",userMail="",userLastConnection = LocalDateTime.now)
        )
        (mockUsers.getAllUsers _).expects().returns(Future(userList)).once()

        val mockProducts = mock[Products]
        val routesUnderTest = new Routes(mockUsers,mockProducts).routes

        val request = HttpRequest(uri = "/users")
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)

            contentType should ===(ContentTypes.`text/html(UTF-8)`)

            entityAs[String].length should be(203)
        }
    }

    test("Route GET /products should display the list of products") {
        var mockUsers = mock[Users]
        var mockProducts = mock[Products]

        val productList = List(
            Product(productName="p0", productId="id0", productDetail="desc0", productPrice=0)
        )
        (mockProducts.getAllProducts _).expects().returns(Future(productList)).once()

        val routesUnderTest = new Routes(mockUsers,mockProducts).routes

        val request = HttpRequest(uri = "/products")
        request ~> routesUnderTest ~> check {
            status should ===(StatusCodes.OK)

            contentType should ===(ContentTypes.`text/html(UTF-8)`)
        }
    }
}

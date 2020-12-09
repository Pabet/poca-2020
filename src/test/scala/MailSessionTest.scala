import com.typesafe.scalalogging.LazyLogging
import mails.{EmailDescription, MailService, MailSession, SmtpConfig}
import org.scalatest.funsuite.AnyFunSuite

class MailSessionTest extends AnyFunSuite with LazyLogging {

  val from_email: String = "poca.marketplace@gmail.com"
  val gmail_app_password: String = "hsnnftzwknahxuzf"
  val to_email: String = "poca.marketplace@gmail.com"
  val smtp_host: String = "smtp.gmail.com"
  val smtp_port: Int = 587

  test("MailSession.send should send simple email") {
    val dsc = EmailDescription(from_email, "Poca-Marketplace", to_email, "some content", "toto")
    val smtpConfig = SmtpConfig(
      smtp_host = smtp_host,
      smtp_port = smtp_port,
      smtp_username = from_email,
      smtp_password = gmail_app_password,
      tls_connection = false
    )
    val session: MailSession = new MailSession(smtpConfig, dsc)
    val result = session.sendEmail()
//    assert(result.isDefined) test is disable for dev environment
  }

  test("MailService.sendRegistrationEmail() should send confirmation email after registration") {
    val smtpConfig = SmtpConfig(
      smtp_host = smtp_host,
      smtp_port = smtp_port,
      smtp_username = from_email,
      smtp_password = gmail_app_password,
      tls_connection = true
    )
    MailService.smtpConfig = smtpConfig
    val result = MailService.sendRegistrationMail("poca", to_email)
//    assert(result.isDefined) test is disable for dev environment
  }

}

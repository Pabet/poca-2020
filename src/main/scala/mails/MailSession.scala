package mails

import java.util.{Date, Properties}

import com.typesafe.scalalogging.LazyLogging
import javax.mail.{Message, Session}
import javax.mail.internet.{InternetAddress, MimeMessage}

case class EmailDescription(
  from: String,
  fromName: String,
  to: String,
  content: String,
  subject: String)

case class SmtpConfig(
  smtp_host: String,
  smtp_port: Int,
  smtp_username: String,
  smtp_password: String,
  tls_connection: Boolean
)

sealed class MailSession(
  val smtpConfig: SmtpConfig,
  val emailDescription: EmailDescription
) extends LazyLogging {

  val properties: Properties = setupSmtpServerProperties(
    smtpConfig.tls_connection,
    smtpConfig.smtp_host,
    smtpConfig.smtp_port)

  val session: Session = Session.getInstance(properties)

  val message = new MimeMessage(session)
  message.setFrom(stringToInternetAddress(emailDescription.from))
  message.setRecipient(Message.RecipientType.TO, stringToInternetAddress(emailDescription.to))
  message.setSubject(emailDescription.subject)
  message.setSentDate(new Date())
  message.setHeader("Content-Type", "text/plain")
  message.setContent(emailDescription.content, "text/html")

  def sendEmail(): Option[Int] = {
    val transport = session.getTransport("smtp")
    try {
      logger.info("Sending email...")
      if (smtpConfig.smtp_username != null &&
        smtpConfig.smtp_password.nonEmpty) {
        transport.connect(
          smtpConfig.smtp_host,
          smtpConfig.smtp_username,
          smtpConfig.smtp_password)
        transport.sendMessage(message, message.getAllRecipients)
        logger.info("Email successfully sent!")
      }
      Some(emailDescription.to.length)
    } catch {
      case e: Exception => println(e.getMessage)
        None
    } finally {
      transport.close()
    }
  }

  private def stringToInternetAddress(field: String): InternetAddress = {
    new InternetAddress(field)
  }

  private def setupSmtpServerProperties(
    tls: Boolean,
    host: String,
    port: Int
  ): Properties = {
    val properties = new Properties()
    properties.put("mail.smtp.host", host)
    properties.put("mail.smtp.port", port)
    properties.put("mail.smtp.starttls.enable", tls)
    properties.put("mail.smtp.auth", "true")
    properties.put("mail.smtp.starttls.required", tls)
    properties.put("mail.debug", "true")
    properties
  }

}

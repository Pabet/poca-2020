package mails

import com.typesafe.config.{Config, ConfigFactory}

object MailService {

  val rootConfig: Config = ConfigFactory.load().getConfig("smtpCredentials")
  val from_email: String = rootConfig.getString("email")
  val gmail_app_password: String = rootConfig.getString("password")
  val smtp_host: String = rootConfig.getString("smtp_host")
  val smtp_port: Int = rootConfig.getString("smtp_port").toInt

  var smtpConfig: SmtpConfig = SmtpConfig(
    smtp_host = smtp_host,
    smtp_port = smtp_port,
    smtp_username = from_email,
    smtp_password = gmail_app_password,
    tls_connection = true
  )

  def sendRegistrationMail(name: String, recipient: String): Option[Int] = {
    val description = Emails.register(name, recipient)
    val session = new MailSession(smtpConfig, description)
    session.sendEmail()
  }
}

package mails

import poca.User

object Emails {

  def register(
    name: String,
    recipient: String
  ): EmailDescription = EmailDescription(
    from = "poca.marketplace@gmail.com",
    fromName = "Marketplace registration",
    to = recipient,
    content = emails.html.registered(name, recipient).body,
    subject = "Marketplace validator suite registration confirmation"
  )

  def resetPassword(
    user: User,
    id: PasswordResetId
  ): EmailDescription = EmailDescription(
    from = "poca.matketplace@gmail.com",
    fromName = "Marketplace password reset",
    to = user.userMail,
    content = "not implement yet",
    subject = "Marketplace validator suite password reset"
  )

  def confirmCommand(user: User, command: CommandConfirmation): EmailDescription = EmailDescription(
    from = "poca.marketplace@gmail.com",
    fromName = "Marketpace command confirmation",
    to = user.userMail,
    content = "to implement yet",
    subject = "Marketplace validator suite command confirmation"
  )

  // not implemented yet
  case class PasswordResetId(id: String)
  case class CommandConfirmation()

}

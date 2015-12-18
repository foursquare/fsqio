// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util

import com.twitter.util.{Future, FuturePool}
import java.util.Properties
import java.util.concurrent.Executors
import javax.mail._
import javax.mail.internet._


trait MailSender {
  def send(to: List[String], cc: List[String], subject: String, message: String): Future[Unit]
}

class ConcreteMailSender extends MailSender with Logger {
  val sender: MailSender = if (!Config.opt(_.getBoolean("email.test")).exists(_ == true)) {
    new MailAndLogSender
  } else {
    logger.info("Using a fake mailer, email.test is true")
    new LogMailSender
  }

  def send(to: List[String], cc: List[String], subject: String, message: String): Future[Unit] = {
    sender.send(to, cc, subject, message)
  }
}

class LogMailSender extends MailSender with Logger {
  def send(to: List[String], cc: List[String], subject: String, message: String): Future[Unit] = {
    logger.info("To:\n%s\nCC:\n%s\nSubject:\n%s\n\n%s".format(
      to.mkString("\n"),
      cc.mkString("\n"),
      subject,
      message
    ))
    Future.Unit
  }
}

class JavaxMailSender extends MailSender {
  val props = new Properties()
  val mailFuturePool = FuturePool(Executors.newFixedThreadPool(5))

  props.setProperty("mail.transport.protocol", "smtp")
  props.setProperty("mail.host", Config.root.getString("email.host"))
  props.put("mail.smtp.auth", "true")
  props.put("mail.smtp.port", "465")
  props.put("mail.smtp.socketFactory.port", "465")
  props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
  props.put("mail.smtp.socketFactory.fallback", "false")
  props.setProperty("mail.smtp.quitwait", "false")
  val session = Session.getDefaultInstance(props, new Authenticator() {
    override def getPasswordAuthentication = new PasswordAuthentication(
      Config.opt(_.getString("email.user")).getOrElse(""),
      Config.opt(_.getString("email.password")).orElse(Config.opt(_.getString("email.pass"))).getOrElse("")
    )
  })

  def send(to: List[String], cc: List[String], subject: String, message: String): Future[Unit] = {
    val mail = new MimeMessage(session)
    mail.setFrom(new InternetAddress(Config.opt(_.getString("email.from")).getOrElse("")))
    mail.addRecipients(Message.RecipientType.TO, to.mkString(","))
    mail.addRecipients(Message.RecipientType.CC, cc.mkString(","))
    mail.setSubject(subject)
    mail.setText(message)
    mailFuturePool(Transport.send(mail))
  }
}


class MailAndLogSender extends MailSender {
  val logMailSender = new LogMailSender
  val mailSender = new JavaxMailSender

  def send(to: List[String], cc: List[String], subject: String, message: String): Future[Unit] = {
    logMailSender.send(to, cc, subject, message)
    mailSender.send(to, cc, subject, message)
  }
}

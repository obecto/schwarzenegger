package com.obecto.schwarzenegger.communicators

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.HttpExt
import com.obecto.schwarzenegger.Engine.IntroduceEngine
import com.obecto.schwarzenegger.messages.{MessageProcessed, MessageReceived}

import scala.concurrent.Future

/**
  * Created by gbarn_000 on 6/12/2017.
  */
trait Communicator extends Actor {
  var engine: ActorRef = _

  def http: HttpExt

  def sendTextResponse(text: String, senderId: String): Unit

  def sendInteractiveResponse(response: Object, senderId: String) = ???

  def startDefaultServer(): Future[ServerBinding] = ???

  def receive = {
    customReceive orElse {
      case IntroduceEngine(_engine) => engine = _engine
      case MessageReceived(text, senderId) =>
        engine ! MessageReceived(text, senderId)
      case MessageProcessed(text, senderId) =>
        sendTextResponse(text, senderId)
    }
  }

  def customReceive: Receive = Map.empty
}

object Communicator {
  def props(communicatorType: Class[_ <: Communicator], server: Future[ServerBinding]): Props = Props.apply(communicatorType, Some(server), None)

  def props(communicatorType: Class[_ <: Communicator], serverConfig: DefaultServerConfig): Props = Props(communicatorType, None, Some(serverConfig))
}







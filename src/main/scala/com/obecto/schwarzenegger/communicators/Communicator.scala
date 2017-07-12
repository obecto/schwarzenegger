package com.obecto.schwarzenegger.communicators

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.HttpExt
import com.obecto.schwarzenegger.Engine.IntroduceEngine
import com.obecto.schwarzenegger.messages.{MessageExternal, MessageProcessed}

import scala.concurrent.Future

/**
  * Created by gbarn_000 on 6/12/2017.
  */
trait Communicator extends Actor {
  var http: HttpExt = _
  var engine: ActorRef = _

  def customReceive: Receive = Map.empty

  def sendTextResponse(text: String, senderId: String): Unit

  def sendInteractiveResponse(response: Object, senderId: String) = ???

  def startDefaultServer(): Future[ServerBinding] = ???

  def receive = {
    customReceive orElse {
      case IntroduceEngine(_engine) => engine = _engine
      case MessageExternal(text, senderId) =>
        engine ! MessageExternal(text, senderId)
      case MessageProcessed(text, senderId) =>
        sendTextResponse(text, senderId)
    }
  }
}

object Communicator {
  def props(communicatorType: Class[_ <: Communicator], server: Future[ServerBinding]): Props = Props.apply(communicatorType, Some(server), None)

  def props(communicatorType: Class[_ <: Communicator], serverConfig: DefaultServerConfig): Props = Props(communicatorType, None, Some(serverConfig))
}







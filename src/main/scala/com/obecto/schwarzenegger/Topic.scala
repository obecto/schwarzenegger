package com.obecto.schwarzenegger


import akka.actor.{ActorRef, FSM, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.pattern._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.obecto.schwarzenegger.Topic._
import com.obecto.schwarzenegger.intent_detection.{ClearIntentCache, IntentData, IntroduceIntentDetector}
import com.obecto.schwarzenegger.messages.HandleMessage
import spray.json.DefaultJsonProtocol

import scala.util.{Failure, Success}

/**
  * Created by Ioan on 13-Jun-17.
  */
abstract class Topic extends FSM[Topic.State, Topic.TransitionData] with DefaultJsonProtocol {

  import context.dispatcher

  implicit val system = context.system
  implicit val timeout = Config.REQUEST_TIMEOUT
  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

  import akka.event.LoggingAdapter

  val logger: LoggingAdapter = Logging.getLogger(context.system, this)
  val http = Http(context.system)

  var intentDetector: ActorRef = _
  var currentSender: ActorRef = _
  var lastIntentData: IntentData = _


  whenUnhandled {
    case Event(message: HandleMessage, _) =>
      currentSender = sender()
      detectIntent(message.text)
      stay()

    case Event(IntroduceIntentDetector(intentDetector: ActorRef), _) =>
      setIntentDetector(intentDetector)
      stay()

    case Event(ClearCache, _) =>
      intentDetector ! ClearIntentCache
      stay()
  }

  def receiveEvent: PartialFunction[Event, String] = {
    case Event(response: IntentData, _) =>
      println(response)
      lastIntentData = response
      response.intent
  }

  def dataChanged: PartialFunction[Event, State] = {
    case Event(dataChanged: DataChanged, _) =>
      println("SharedData is changed: " + dataChanged)
      stay()
  }

  def subscribeFor(key: String, initialData: Option[SharedData] = None): Unit = {
    context.parent ! Subscribe(key, initialData)
  }

  def unSubscribeFor(key: String): Unit = {
    context.parent ! Unsubscribe(key)
  }

  def registerMessageNotHandled(): Unit = {
    currentSender ! false
  }

  def exterminate(): Unit = {
    context.parent ! Exterminate(self)
  }

  def addTopicInConversation(topicClass: Class[_ <: Topic], isStatic: Boolean = false): Unit = {
    context.parent ! TopicDescriptorType(topicClass, isStatic)
  }

  def changeData(key: String, newData: SharedData): Unit = {
    context.parent ! DataChanged(key, newData)
  }

  def setIntentDetector(intentDetector: ActorRef): Unit = {
    this.intentDetector = intentDetector
  }

  private def detectIntent(message: String): Unit = {
    val answer = intentDetector ? message
    answer.onComplete {
      case Success(response: IntentData) =>
        self ! response
      case Failure(fail) =>
        sendTextResponseAndRegisterMessageHandled("Something went wrong...")
    }
  }

  def sendTextResponseAndRegisterMessageHandled(text: String): Unit = {
    registerMessageHandled()
    sendTextResponse(text)
  }

  def sendTextResponse(text: String): Unit = {
    context.parent ! HandleMessage(text)
  }


  def registerMessageHandled(): Unit = {
    currentSender ! true
    intentDetector ! ClearIntentCache
  }
}

object Topic {

  def props(topicType: Class[_ <: Topic]): Props = Props.apply(topicType)

  sealed trait TransitionData

  trait State

  case object InActive extends State

  case object ActivateTopic
  case object DeactivateTopic

  case object ClearCache

  case object EmptyTransitionData extends TransitionData

}

case class TopicDescriptorType(topicClass: Class[_ <: Topic], isStatic: Boolean = false)

case class TopicDescriptor(fsm: ActorRef, isStatic: Boolean = false)

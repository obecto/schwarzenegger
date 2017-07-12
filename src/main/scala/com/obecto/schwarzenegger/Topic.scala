package com.obecto.schwarzenegger


import akka.actor.{ActorRef, FSM, Props}
import akka.http.scaladsl.Http
import akka.pattern._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.obecto.schwarzenegger.Topic.ClearCache
import com.obecto.schwarzenegger.intent_detection.{ClearIntentCache, IntentData, IntroduceIntentDetector}
import com.obecto.schwarzenegger.messages.MessageInternal
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

  val http = Http(context.system)

  var intentDetector: ActorRef = _
  var currentSender: ActorRef = _
  var lastIntentData: IntentData = _

  whenUnhandled {
    case Event(message: MessageInternal, _) =>
    //  println("HandleMessage and trying to detect intent " + message + " and sender is : " + sender())
      currentSender = sender()
      detectIntent(message)
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
      //implicit val params = response.params
      stay()
  }

  def subscribeFor(key: String, initialData: Option[SharedData] = None): Unit = {
    //println("Trying to subscribe... " + context.parent)
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

  def addTopic(topicClass: Class[_ <: Topic], isStatic: Boolean = false): Unit = {
    context.parent ! TopicDescriptorType(topicClass, isStatic)
  }

  def changeData(key: String, newData: SharedData): Unit = {
    context.parent ! DataChanged(key, newData)
  }

  def setIntentDetector(intentDetector: ActorRef): Unit = {
    this.intentDetector = intentDetector
  }

  private def detectIntent(message: MessageInternal): Unit = {
    val answer = intentDetector ? message
    answer.onComplete {
      case Success(response: IntentData) =>
       // println("Response from intent detector is : " + response)
        self ! response
      case Failure(fail) =>
       // println("Unable to get response from intent detector" + fail)
        sendTextResponse("Something went wrong...")
    }
  }

  def sendTextResponse(text: String, withoutRegisteringMessageHandled: Boolean = false): Unit = {
    if (!withoutRegisteringMessageHandled) {
      registerMessageHandled()
    }
    context.parent ! MessageInternal(text)
  }

  def registerMessageHandled(): Unit = {
    intentDetector ! ClearIntentCache
    currentSender ! true
  }
}

object Topic {

  def props(topicType: Class[_ <: Topic]): Props = Props.apply(topicType)
  sealed trait TransitionData
  trait State
  case object Waiting extends State
  case object ClearCache extends State
  case object EmptyTransitionData extends TransitionData

}

case class Result(isHandled: Boolean, intentDetectorCache: String = "")
case class TopicDescriptorType(topicClass: Class[_ <: Topic], isStatic: Boolean = false)
case class TopicDescriptor(fsm: ActorRef, isStatic: Boolean = false)

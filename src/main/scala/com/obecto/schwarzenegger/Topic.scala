package com.obecto.schwarzenegger


import java.util.UUID

import akka.actor.{ActorRef, FSM, Props}
import akka.event.Logging
import akka.event.jul.Logger
import akka.http.scaladsl.Http
import akka.pattern._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.obecto.schwarzenegger.Topic._
import com.obecto.schwarzenegger.example.General
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

  /*
  var lastActiveState: Option[Topic.State] = None

  startWith(InActive, EmptyTransitionData)

  when(InActive){
    case Event(ActivateTopic, _) =>
      if (lastActiveState.nonEmpty){
        topicActivated()
        goto(lastActiveState.get)
      } else {
        logger.warning("No lastActiveState recorder. Staying in InActive state")
        stay()
      }
  }
  */

  whenUnhandled {
    case Event(message: HandleMessage, _) =>
      //  println("HandleMessage and trying to detect intent " + message + " and sender is : " + sender())
      currentSender = sender()
      detectIntent(message.text)
      stay()

    case Event(IntroduceIntentDetector(intentDetector: ActorRef), _) =>
      setIntentDetector(intentDetector)
      stay()

    case Event(ClearCache, _) =>
      intentDetector ! ClearIntentCache
      stay()

    /*
    case Event(DeactivateTopic, _) =>
      if(!this.stateName.equals(InActive)){
        topicDeactivated()
        lastActiveState = Some(this.stateName)
        goto(InActive)
      }
      stay()
    */
  }

  /*
  def topicActivated(): Unit ={
      currentSender ! Activated
  }

  def topicDeactivated(): Unit ={

  }

  def setInitialState(state: Topic.State): Unit = {
    lastActiveState = Some(state)
  }
  */

  def receiveEvent: PartialFunction[Event, String] = {
    case Event(response: IntentData, _) =>
      println(response)
      lastIntentData = response
      //TODO Check if current state can handle anything and call initialize if it cans
      /*if(response.intent.equals("greetings")){
        initialize()
      }*/
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

  private def detectIntent(message: String): Unit = {
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
    context.parent ! HandleMessage(text)
  }

 /* val sentMessageTokens:Map[UUID, Boolean] = Map.empty

  def sendTextResponseOnce(token:UUID, text: String, withoutRegisteringMessageHandled: Boolean = false): Unit = {
    if(!sentMessageTokens(token)){
      sentMessageTokens(token) = true;
      sendTextResponse(text, withoutRegisteringMessageHandled);
    }
  }*/

  def registerMessageHandled(): Unit = {
    intentDetector ! ClearIntentCache
    currentSender ! true
  }
}

object Topic {

  def props(topicType: Class[_ <: Topic]): Props = Props.apply(topicType)

  sealed trait TransitionData

  trait State

  case object InActive extends State

  case object ActivateTopic
  case object DeactivateTopic

  //case object Activated
  //case object Deactivated

  case object ClearCache

  case object EmptyTransitionData extends TransitionData

}

case class TopicDescriptorType(topicClass: Class[_ <: Topic], isStatic: Boolean = false)

case class TopicDescriptor(fsm: ActorRef, isStatic: Boolean = false)

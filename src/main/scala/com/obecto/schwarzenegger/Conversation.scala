package com.obecto.schwarzenegger

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern._
import com.obecto.schwarzenegger.Topic.ClearCache
import com.obecto.schwarzenegger.intent_detection.{IntentDetector, IntroduceIntentDetector}
import com.obecto.schwarzenegger.messages.{MessageInternal, MessageProcessed}
import com.obecto.schwarzenegger.translators.Translator

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, TimeoutException}
import scala.util.{Failure, Success}

/**
  * Created by gbarn_000 on 6/21/2017.
  */
class Conversation(senderId: String, topicDescriptorTypes: List[TopicDescriptorType],
                   engine: ActorRef, translatorType: Class[_ <: Translator],
                   intentDetectorType: Class[_ <: IntentDetector]) extends Actor {

  implicit val timeout = Config.REQUEST_TIMEOUT
  val translator = initTranslator(translatorType)
  private val topicDescriptors: ListBuffer[TopicDescriptor] = ListBuffer.empty
  private val sharedData = mutable.Map[String, DataAndSubscribers]()
  private val defaultIntentDetector: ActorRef = initIntentDetector(intentDetectorType)
  context.watch(defaultIntentDetector)
  context.watch(translator)


  def initIntentDetector(intentDetectorType: Class[_ <: IntentDetector]): ActorRef = {
    context.actorOf(IntentDetector.props(intentDetectorType), intentDetectorType.getSimpleName)
  }

  def initTranslator(translatorType: Class[_ <: Translator]): ActorRef = {
    context.actorOf(Translator.props(translatorType), translatorType.getSimpleName)
  }

  override def preStart(): Unit = {
    // Initialize children topics
    for (descriptorIndex <- topicDescriptorTypes.indices) {
      val currentDescriptorType = topicDescriptorTypes(descriptorIndex)
      val descriptorStaticFlag = currentDescriptorType.isStatic
      try {
        addNext(currentDescriptorType.topicClass, descriptorStaticFlag)
      } catch {
        case illegalArgumentException: IllegalArgumentException =>
          illegalArgumentException.printStackTrace()
      }
    }
  }

  def addNext(topicClass: Class[_ <: Topic], isStatic: Boolean): Unit = {
    if (sameTypeTopicExists(topicClass)) {
      //  println("Topic class EXIST!!!!!!!!!!!!!!!!!!!!!")
      throw new IllegalArgumentException("Topic of same type already exist!")
    }
    val topicActor = context.actorOf(Topic.props(topicClass), topicClass.getSimpleName)
    context.watch(topicActor)
    topicDescriptors.+=(TopicDescriptor(topicActor, isStatic))
    introduceToTopic(topicActor, defaultIntentDetector)
  }

  def introduceToTopic(topicFSM: ActorRef, intentDetector: ActorRef): Unit = {
    topicFSM ! IntroduceIntentDetector(intentDetector)
  }

  def sameTypeTopicExists(topicClass: Class[_ <: Topic]): Boolean = {
    for (topicDescriptor <- topicDescriptors) {
      if (topicDescriptor.fsm.path.name.equals(topicClass.getSimpleName)) {
        return true
      }
    }
    false
  }

  // Overriding postRestart to disable the call to preStart() after restarts
  override def postRestart(reason: Throwable): Unit = ()

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    // Keep the call to postStop(), but no stopping of children
    postStop()
  }

  override def postStop(): Unit = {
    super.postStop()
  }

  def receive = {

    case MessageInternal(text) =>
      engine ! MessageProcessed(text, senderId)

    case text: String =>
      val translateFuture = translator ? text
      translateFuture.onComplete {
        case Success(translatedText: String) =>
          //    println("Translated text: " + translatedText)
          findAndSwitchHandlingTopic(translatedText)
        case Failure(fail) => //println("Could not translate text : " + fail.getMessage)
      }

    case topicDescriptorType: TopicDescriptorType =>
      try {
        addFront(topicDescriptorType.topicClass, topicDescriptorType.isStatic)
      } catch {
        case illegalArgumentException: IllegalArgumentException =>
          illegalArgumentException.printStackTrace()
      }

    case exterminate: Exterminate =>
      exterminateTopic(exterminate.topic)

    case subscription: Subscribe =>
      subscribeForData(subscription.key, subscription.data, sender())

    case unsubscribe: Unsubscribe =>
      try {
        unsubscribeFromData(unsubscribe.key, sender())
      } catch {
        case noSuchElement: NoSuchElementException => noSuchElement.printStackTrace()
      }

    case changedData: DataChanged =>
      changeData(changedData.key, changedData.data)
  }

  def changeData(key: String, data: SharedData): Unit = {
    // println("Shared data is being changed..." + " Key: " + key + " Data: " + data)
    sharedData.get(key) match {
      case Some(dataAndSubscribers) =>
        dataAndSubscribers.optionalData = Some(data)
        notifySubscribersDataChanged(key, data, dataAndSubscribers.subscribers)
      case None =>
        sharedData.+=(key -> DataAndSubscribers(Some(data), ListBuffer.empty))
    }
  }

  def notifySubscribersDataChanged(key: String, data: SharedData, subscribers: ListBuffer[ActorRef]): Unit = {
    println("Notify subscribers data changed... " + subscribers)
    subscribers.foreach(subscriber =>
      subscriber ! DataChanged(key, data)
    )
  }

  def subscribeForData(key: String, data: Option[SharedData], subscriber: ActorRef): Unit = {
    println(subscriber + " Subscribed for changes in data - key : " + key + " data : " + data)
    sharedData.get(key) match {
      case Some(dataAndSubscribers) =>
        dataAndSubscribers.subscribers.+=:(subscriber)
      case None =>
        sharedData.+=(key -> DataAndSubscribers(data, ListBuffer(subscriber)))
    }
  }

  def unsubscribeFromData(key: String, subscriber: ActorRef): Unit = {
    sharedData.get(key) match {
      case Some(dataAndSubscribers) =>
        if (dataAndSubscribers.subscribers.contains(subscriber)) {
          dataAndSubscribers.subscribers.-=(subscriber)
        } else {
          throw new NoSuchElementException("Topic is not subscribed.")
        }
      case None =>
        throw new NoSuchElementException("Data not found.")
    }
  }

  def addFront(topicClass: Class[_ <: Topic], isStatic: Boolean): Unit = {
    if (sameTypeTopicExists(topicClass)) {
      println("Topic class EXIST!!!!!!!!!!!!!!!!!!!!!")
      throw new IllegalArgumentException("Topic of same type already exist!")
    }
    val topicActor = context.actorOf(Topic.props(topicClass), topicClass.getSimpleName)
    println(topicActor.path + " created")
    context.watch(topicActor)
    topicDescriptors.+=:(TopicDescriptor(topicActor, isStatic))
    introduceToTopic(topicActor, defaultIntentDetector)
  }

  def exterminateTopic(topic: ActorRef): Unit = {
    for (currentTopicDescriptor <- topicDescriptors) {
      if (currentTopicDescriptor.fsm.equals(topic)) {
        println("Topic is being exterminated" + currentTopicDescriptor.fsm)
        context.unwatch(currentTopicDescriptor.fsm)
        currentTopicDescriptor.fsm ! PoisonPill
        topicDescriptors.-=(currentTopicDescriptor)
        return
      }
    }
  }

  def findAndSwitchHandlingTopic(text: String): Unit = {
    println("Conversation received text to handle: " + text)
    try {
      for (topicIndex: Int <- topicDescriptors.indices) {
        val topicDescriptor = topicDescriptors(topicIndex)
        //println("Descriptor trying to handle message: " + topicDescriptor.fsm.path)
        val topicResponseFuture = Patterns.ask(topicDescriptor.fsm, MessageInternal(text), timeout.duration)
        val isHandled: Boolean = Await.result(topicResponseFuture, timeout.duration).asInstanceOf[Boolean]
        println("Is message handled from topic " + topicDescriptor.fsm.path.name + " ? " + isHandled)
        if (isHandled) {
          if (topicIndex > 0 && !topicDescriptor.isStatic) {
            topicDescriptors.-=(topicDescriptor)
            topicDescriptors.+=:(topicDescriptor)
            // println("Order of topics is changed to: " + topicDescriptors)
          }
          return
        }
      }
    }
    catch {
      case timeOutException: TimeoutException =>
        timeOutException.printStackTrace()
    }
    for (topicIndex: Int <- topicDescriptors.indices) {
      val topicDescriptor = topicDescriptors(topicIndex)
      topicDescriptor.fsm ! ClearCache
    }
  }
}

object Conversation {

  val activeConversations: ListBuffer[ActorRef] = new ListBuffer[ActorRef]()

  def getOrCreate(senderId: String)(implicit conversationParams: ConversationParams): ActorRef = {
    //println("Active conversations are: " + activeConversations)
    for (conversationIndex <- activeConversations.indices) {
      val currentConversation = activeConversations(conversationIndex)
      if (currentConversation.path.name.equals(senderId)) {
        //  println("found conversation for sender id: " + senderId)
        if (activeConversations.lengthCompare(1) > 0) {
          activeConversations.-=(currentConversation)
          activeConversations.+=:(currentConversation)
          //  println("Active conversations changed: " + activeConversations)
        }
        return currentConversation
      }
    }
    // println("No conversation for sender id and creating new... " + senderId)
    val descriptorTypes = conversationParams.descriptorTypes
    val system = conversationParams.system
    val newConversation = system.actorOf(props(senderId, descriptorTypes, conversationParams), senderId)
    activeConversations.+=:(newConversation)
    newConversation
  }

  def props(senderId: String, topicDescriptorTypes: List[TopicDescriptorType], conversationParams: ConversationParams) = Props(classOf[Conversation],
    senderId,
    topicDescriptorTypes,
    conversationParams.engine,
    conversationParams.translatorType,
    conversationParams.intentDetectorType)
}

case class ConversationParams(descriptorTypes: List[TopicDescriptorType], engine: ActorRef,
                              intentDetectorType: Class[_ <: IntentDetector],
                              translatorType: Class[_ <: Translator],
                              system: ActorSystem)

case class Exterminate(topic: ActorRef)

trait SharedData

case class Subscribe(key: String, data: Option[SharedData] = None)

case class Unsubscribe(key: String)

case class DataChanged(key: String, data: SharedData)

case class DataAndSubscribers(var optionalData: Option[SharedData], subscribers: ListBuffer[ActorRef])

package com.obecto.schwarzenegger

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern._
import com.obecto.schwarzenegger.Topic.ClearCache
import com.obecto.schwarzenegger.intent_detection.{IntentDetector, IntroduceIntentDetector}
import com.obecto.schwarzenegger.messages.{HandleMessage, MessageProcessed}
import com.obecto.schwarzenegger.translators.Translator

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
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
        val topicDescriptor = initializeTopicDescriptor(currentDescriptorType.topicClass, descriptorStaticFlag)
        appendTopicDescriptorToBuffer(topicDescriptor)
        introduceIntentDetectorToTopic(topicDescriptor.fsm, defaultIntentDetector)
      } catch {
        case illegalArgumentException: IllegalArgumentException =>
          illegalArgumentException.printStackTrace()
      }
    }
  }

  private def appendTopicDescriptorToBuffer(topicDescriptor: TopicDescriptor): ListBuffer[TopicDescriptor] = {
    topicDescriptors += topicDescriptor
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

    case HandleMessage(text) =>
      engine ! MessageProcessed(text, senderId)

    case untranslatedText: String =>
      askTranslatorForTranslation(untranslatedText)

    case topicToPrepend: TopicDescriptorType =>
      val topicDescriptor = initializeTopicDescriptor(topicToPrepend.topicClass, topicToPrepend.isStatic)
      prependTopicDescriptorToBuffer(topicDescriptor)
      introduceIntentDetectorToTopic(topicDescriptor.fsm, defaultIntentDetector)

    case exterminate: Exterminate =>
      exterminateTopic(exterminate.topic)

    case Subscribe(key: String, withData: Option[SharedData]) =>
      if (withData.nonEmpty)
        createNewDataRecord(key, withData.get)
      addNewSubscriberToData(sender(), key)

    case Unsubscribe(key: String) =>
      unsubscribeFromData(sender(), key)

    case DataChanged(key: String, data: SharedData) =>
      changeData(key, data)
  }

  private def initializeTopicDescriptor(topicClass: Class[_ <: Topic], isStatic: Boolean): TopicDescriptor = {
    if (sameTypeTopicExists(topicClass)) {
      throw new IllegalArgumentException("Topic of same type already exist!")
    }
    val topicActor = initialiseTopicActor(topicClass)
    createTopicDescriptor(topicActor, isStatic)
  }

  private def createTopicDescriptor(topicFSM: ActorRef, isStatic: Boolean): TopicDescriptor = {
    TopicDescriptor(topicFSM, isStatic)
  }

  private def initialiseTopicActor(topicClass: Class[_ <: Topic]): ActorRef = {
    val topicActor = context.actorOf(Topic.props(topicClass), topicClass.getSimpleName)
    context.watch(topicActor)
    topicActor
  }

  private def sameTypeTopicExists(topicClass: Class[_ <: Topic]): Boolean = {
    for (topicDescriptor <- topicDescriptors) {
      if (topicDescriptor.fsm.path.name.equals(topicClass.getSimpleName)) {
        return true
      }
    }
    false
  }

  private def introduceIntentDetectorToTopic(topicFSM: ActorRef, intentDetector: ActorRef): Unit = {
    topicFSM ! IntroduceIntentDetector(intentDetector)
  }

  private def prependTopicDescriptorToBuffer(topicDescriptor: TopicDescriptor): ListBuffer[TopicDescriptor] = {
    topicDescriptors.+=:(topicDescriptor)

  }

  private def askTranslatorForTranslation(text: String): Unit = {
    val translateFuture = translator ? text
    translateFuture.onComplete {
      case Success(translatedText: String) =>
        findAndSwitchHandlingTopic(translatedText)
      case Failure(fail) =>
    }
  }

  private def findAndSwitchHandlingTopic(text: String): Unit = {
    println("Conversation received text to handle: " + text)
    try {
      for (topicIndex: Int <- topicDescriptors.indices) {
        val currentTopicDescriptor = topicDescriptors(topicIndex)
        if (canTopicFSMHandleMessage(currentTopicDescriptor.fsm, text)) {
          if (topicIndex > 0)
            bringTopicDescriptorToFrontIfNotStatic(currentTopicDescriptor)
          return
        }
      }
      sendClearCacheMessageToTopicFSM()
    }
    catch {
      case exception: Exception =>
        exception.printStackTrace()
    }
  }


  private def canTopicFSMHandleMessage(topicFSM: ActorRef, text: String): Boolean = {
    val topicResponseFuture = Patterns.ask(topicFSM, HandleMessage(text), timeout.duration)
    val isHandled = Await.result(topicResponseFuture, timeout.duration).asInstanceOf[Boolean]
    println("Is message handled from topic " + topicFSM.path.name + " ? " + isHandled)
    isHandled
  }

  private def bringTopicDescriptorToFrontIfNotStatic(topicDescriptor: TopicDescriptor): ListBuffer[TopicDescriptor] = {
    if (!topicDescriptor.isStatic) {
      topicDescriptors.-=(topicDescriptor)
      topicDescriptors.+=:(topicDescriptor)
    }
    topicDescriptors
  }

  private def sendClearCacheMessageToTopicFSM(): Unit = {
    for (topicIndex: Int <- topicDescriptors.indices) {
      val topicDescriptor = topicDescriptors(topicIndex)
      topicDescriptor.fsm ! ClearCache
    }
  }


  private def changeData(key: String, data: SharedData): Unit = {
    extractDataAndSubscribers(key) match {
      case Some(dataAndSubscribers) =>
        dataAndSubscribers.optionalData = Some(data)
        notifySubscribersDataChanged(key, data, dataAndSubscribers.subscribers)
      case None =>
        createNewDataRecord(key, data)
    }
  }

  private def notifySubscribersDataChanged(key: String, data: SharedData, subscribers: ListBuffer[ActorRef]): Unit = {
    subscribers.foreach(subscriber =>
      subscriber ! DataChanged(key, data)
    )
  }

  private def createNewDataRecord(key: String, data: SharedData): mutable.Map[String, DataAndSubscribers] = {
    sharedData.+=(key -> DataAndSubscribers(Some(data), ListBuffer.empty))
  }

  private def addNewSubscriberToData(subscriber: ActorRef, key: String): ListBuffer[ActorRef] = {
    extractDataAndSubscribers(key) match {
      case Some(dataAndSubscribers) =>
        dataAndSubscribers.subscribers.+=:(subscriber)
      case None =>
        throw new NoSuchElementException("There was no such data type to subscribe to!")
    }
  }

  private def extractDataAndSubscribers(key: String): Option[DataAndSubscribers] = {
    sharedData.get(key) match {
      case Some(dataAndSubscribers) =>
        Some(dataAndSubscribers)
      case None => None
    }
  }

  private def unsubscribeFromData(subscribedActor: ActorRef, key: String): Unit = {
    extractDataAndSubscribers(key) match {
      case Some(dataAndSubscribers) =>
        if (dataAndSubscribers.subscribers.contains(subscribedActor)) {
          dataAndSubscribers.subscribers.-=(subscribedActor)
        } else {
          throw new NoSuchElementException("Topic is not subscribed.")
        }
      case None =>
    }
  }

  private def exterminateTopic(topic: ActorRef): Unit = {
    for (currentTopicDescriptor <- topicDescriptors) {
      if (currentTopicDescriptor.fsm.equals(topic)) {
        println("Topic is being exterminated" + currentTopicDescriptor.fsm)
        killActor(currentTopicDescriptor.fsm)
        removeTopicDescriptorFromBuffer(currentTopicDescriptor)
        return
      }
    }
  }

  private def removeTopicDescriptorFromBuffer(topicDescriptor: TopicDescriptor): ListBuffer[TopicDescriptor] = {
    topicDescriptors.-=(topicDescriptor)
  }

  private def killActor(actorRef: ActorRef): ActorRef = {
    context.unwatch(actorRef)
    actorRef ! PoisonPill
    actorRef
  }
}

object Conversation {

  val activeConversations: ListBuffer[ActorRef] = new ListBuffer[ActorRef]()

  def getOrCreate(senderId: String)(implicit conversationParams: ConversationParams): ActorRef = {
    searchForExistingConversationActor(senderId) match {
      case Some(conversationFound) => conversationFound
      case None => createActiveConversation(senderId, conversationParams)
    }
  }

  private def searchForExistingConversationActor(senderId: String): Option[ActorRef] = {
    for (conversationIndex <- activeConversations.indices) {
      val currentConversation = activeConversations(conversationIndex)
      if (currentConversation.path.name.equals(senderId)) {
        sendConversationActorToFrontIfOthersExist(currentConversation)
        return Some(currentConversation)
      }
    }
    None
  }

  private def sendConversationActorToFrontIfOthersExist(conversationActor: ActorRef): ListBuffer[ActorRef] = {
    if (activeConversations.lengthCompare(1) > 0) {
      activeConversations.-=(conversationActor)
      activeConversations.+=:(conversationActor)
    }
    activeConversations
  }

  private def createActiveConversation(senderId: String, conversationParams: ConversationParams): ActorRef = {
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

case class Subscribe(key: String, withData: Option[SharedData] = None)

case class Unsubscribe(key: String)

case class DataChanged(key: String, data: SharedData)

case class DataAndSubscribers(var optionalData: Option[SharedData], subscribers: ListBuffer[ActorRef])

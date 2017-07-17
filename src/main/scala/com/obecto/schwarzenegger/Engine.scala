package com.obecto.schwarzenegger

import akka.actor.{Actor, ActorRef, Props}
import com.obecto.schwarzenegger.intent_detection.IntentDetector
import com.obecto.schwarzenegger.messages.{MessageReceived, MessageProcessed}
import com.obecto.schwarzenegger.translators.Translator
import spray.json._


class Engine(communicator: ActorRef, translatorType: Class[_ <: Translator], intentDetectorType: Class[_ <: IntentDetector], topicDescriptorTypes: List[TopicDescriptorType]) extends Actor {

  import Engine._

  implicit val system = context.system
  implicit val timeout = Config.REQUEST_TIMEOUT
  //final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

  communicator ! IntroduceEngine(self)

  def receive = {
    case MessageReceived(text, senderId) =>
      implicit val conversationParams: ConversationParams = ConversationParams(topicDescriptorTypes, self, intentDetectorType, translatorType, system)
      val currentConversation = Conversation.getOrCreate(senderId)
      currentConversation ! text

    case MessageProcessed(text, senderId) =>
      //     println("Message returned : " + text)
      communicator ! MessageProcessed(text, senderId)
  }
}

object Engine extends DefaultJsonProtocol {

  def props(communicator: ActorRef, translatorType: Class[_ <: Translator], intentDetectorType: Class[_ <: IntentDetector], topicDescriptors: List[TopicDescriptorType]): Props =
    Props(classOf[Engine], communicator, translatorType, intentDetectorType, topicDescriptors)

  case class IntroduceEngine(engine: ActorRef)

}
package com.obecto.schwarzenegger.example


import com.obecto.schwarzenegger.Topic.EmptyTransitionData
import com.obecto.schwarzenegger.intent_detection.IntentData
import com.obecto.schwarzenegger.{DataChanged, Topic}

/**
  * Created by gbarn_000 on 6/16/2017.
  */
class ExampleTopic extends Topic {

  // override intentDetector =  someIntentDetector

  subscribeFor("user_name")

  startWith(Waiting, EmptyTransitionData)

  override def dataChanged = {
    case Event(dataChanged: DataChanged, _) =>
      sendTextResponse("data changed in ExampleTopic... " + dataChanged.key + " -> " + dataChanged.data, withoutRegisteringMessageHandled = true)
      stay()
  }

  override def receiveEvent = {
    case Event(response: IntentData, _) =>
      response.intent
  }


  when(Waiting) {

    dataChanged orElse receiveEvent andThen {
      case "greetings" =>
        //Logic here
        sendTextResponse("Zdrasti :)")
        // addTopic(classOf[NameTopic])
        stay() //or goto

      case _ =>
        registerMessageNotHandled()
        stay()
    }
  }

  //override def setIntentDetector(intentDetector : ActorRef = null): Unit = {}

  initialize()
}



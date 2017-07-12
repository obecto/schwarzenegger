package com.obecto.schwarzenegger.example

import com.obecto.schwarzenegger.Topic
import com.obecto.schwarzenegger.Topic.{EmptyTransitionData, Waiting}

/**
  * Created by gbarn_000 on 6/16/2017.
  */
class DateTopic extends Topic {

  // override intentDetector =  someIntentDetector

  subscribeFor("date")

  startWith(Waiting, EmptyTransitionData)

  /*override def dataChanged = {
    case Event(dataChanged,_) =>
      stay()
  }

  override def receiveEvent ={
    case Event(response : IntentData, _ ) =>
      response.intent
  }*/

  when(Waiting) {
    dataChanged orElse receiveEvent andThen {
      case "smalltalk.greetings.hello" =>

        //Logic here
        sendTextResponse("Some text2")
        stay() //or goto
      case "date.day_of_week" =>

        //Logic here
        sendTextResponse("Today is Friday")
        stay() //or goto
      case "date.get" =>
        //Logic here
        sendTextResponse("Today is march 3rd 1878")


        stay() //or goto
      case _ =>
        println("WE ARE ON DEFAULT INTENT CASE")
        registerMessageNotHandled()
        stay()
    }
  }


  //override def setIntentDetector(intentDetector : ActorRef = null): Unit = {}


  initialize()
}

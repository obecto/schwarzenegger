package com.obecto.schwarzenegger.example

import com.obecto.schwarzenegger.Topic
import com.obecto.schwarzenegger.Topic.EmptyTransitionData

/**
  * Created by gbarn_000 on 6/16/2017.
  */
class DateTopic extends Topic {

  startWith(General, EmptyTransitionData)


  onTransition {
    case x -> General =>
      println("Date topic general ")

      if(x.equals(General)){
        sendTextResponse("zdrasti blablbla, DATE topic activated")
      }

      sendTextResponse("freelancer li si")
  }

  when(General) {
    receiveEvent andThen {
      case "greetings" =>
        sendTextResponseAndRegisterMessageHandled("zdrasti :)")
        stay()

      case "general.positive" =>
        sendTextResponseAndRegisterMessageHandled("blabla a imash li registraciq bulstat")
        goto(HaveBulstat)

      case "general.negative" =>
        sendTextResponseAndRegisterMessageHandled("za kakvo iskash da si govorim")
        goto(General)

      case _ =>
        sendTextResponseAndRegisterMessageHandled("ne razbrah dali si freelancer")
        stay()
    }
  }


  // override intentDetector =  someIntentDetector
/*

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
*/


  //override def setIntentDetector(intentDetector : ActorRef = null): Unit = {}


  //initialize()
}

case object Waiting extends Topic.State
package com.obecto.schwarzenegger.example

import com.obecto.schwarzenegger.Topic.{EmptyTransitionData, State}
import com.obecto.schwarzenegger.{SharedData, Topic}

/**
  * Created by gbarn_000 on 6/16/2017.
  */
class NameTopic extends Topic {

  // override intentDetector =  someIntentDetector

  var userName = ""

  startWith(RegisteringName, EmptyTransitionData)


  when(RegisteringName) {
    receiveEvent andThen {
      case "introducing" =>
        userName = lastIntentData.params.getOrElse("name", "")
        sendTextResponse(s"Are you sure your name is $userName?")
        goto(ConfirmName)
      case _ =>
        sendTextResponse("Please, write your name!")

        stay()
    }
  }

  when(ConfirmName) {
    receiveEvent andThen {
      case "confirm_name" =>
        sendTextResponse("Ok")
        println("Changing shared data with " + userName)
        changeData("user_name", UserName(userName))
        exterminate()
        stay()
      case _ =>
        sendTextResponse("Please, confirm your name!")
        stay()
    }
  }

  //override def setIntentDetector(intentDetector : ActorRef = null): Unit = {}

  initialize()
}

case class UserName(name: String) extends SharedData

case object RegisteringName extends State

case object ConfirmName extends State


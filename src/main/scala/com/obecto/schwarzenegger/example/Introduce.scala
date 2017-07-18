package com.obecto.schwarzenegger.example

import java.util.UUID

import com.obecto.schwarzenegger.Topic
import com.obecto.schwarzenegger.Topic.{EmptyTransitionData, State}

/**
  * Created by gbarn_000 on 7/12/2017.
  */
class Introduce extends Topic {


  startWith(General, EmptyTransitionData)

  onTransition {
    case x -> General =>
      println("Entering general from ")
      println(x)

      if(x.equals(General)){
        sendTextResponse("zdrasti blablbla, topic activated",withoutRegisteringMessageHandled = true)
      }

      sendTextResponse("freelancer li si",withoutRegisteringMessageHandled = true)
  }

  when(General) {
    receiveEvent andThen {
      case "greetings" =>
        sendTextResponse("zdrasti :)")
        stay()

      case "general.positive" =>
        sendTextResponse("blabla a imash li registraciq bulstat")
        goto(HaveBulstat)

      case "general.negative" =>
        sendTextResponse("za kakvo iskash da si govorim")
        goto(General)

      case _ =>
        sendTextResponse("ne razbrah dali si freelancer")
        stay()
    }
  }

  when(FreelanceType) {
    receiveEvent andThen {
      case "freelance.positive" =>
        sendTextResponse("blabla a imash li registraciq bulstat")
        goto(HaveBulstat)

      case "freelance.negative" =>
        sendTextResponse("za kakvo iskash da si govorim")
        goto(General)

      case _ =>
        sendTextResponse("ne razbrah dali si freelancer")
        stay()

    }
  }

  when(HaveBulstat) {
    receiveEvent andThen {
      case "bulstat.positive" =>
        sendTextResponse("iskash li da ti razkaja kakvo moga da napravq za teb")
        goto(General)
      case "bulstat.negative" =>
        sendTextResponse("iskash li registraciq blabla")
        //Open modal topic to introduce service
        goto(General)

      case "bulstat.unknown" =>
        sendTextResponse("Az moga da provq dali go imash, no za celta shte mi trqbva ime i egn")
        // Proverka za bulstat v sistemata
        stay()

      case _ =>
        sendTextResponse("Ne moga da produlja predi da mi kajesh dali imash bulstat")
        stay()
    }
  }

  //TODO Init function calls goto("state you started with")
  initialize()

}


case object General extends State

case object FreelanceType extends State

case object HaveBulstat extends State
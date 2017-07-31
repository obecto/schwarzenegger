package com.obecto.schwarzenegger.example

import com.obecto.schwarzenegger.Topic
import com.obecto.schwarzenegger.Topic.EmptyTransitionData

/**
  * Created by Ioan on 22-Jun-17.
  */
class DefaultTopic extends Topic {
  // override intentDetector =  someIntentDetector


  startWith(Waiting, EmptyTransitionData)

  onTransition{
    case x -> Waiting =>
      println("Hello from default topic")
  }


  when(Waiting) {
    receiveEvent andThen {
      _ =>
        sendTextResponseAndRegisterMessageHandled("Sorry, I can't answer. DefaultTopic")
        stay()
    }
  }
  //override def setIntentDetector(intentDetector : ActorRef = null): Unit = {}

  //initialize()
}

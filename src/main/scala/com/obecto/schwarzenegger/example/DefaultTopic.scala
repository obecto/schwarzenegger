package com.obecto.schwarzenegger.example

import com.obecto.schwarzenegger.Topic
import com.obecto.schwarzenegger.Topic.{EmptyTransitionData, Waiting}

/**
  * Created by Ioan on 22-Jun-17.
  */
class DefaultTopic extends Topic {
  // override intentDetector =  someIntentDetector


  startWith(Waiting, EmptyTransitionData)


  when(Waiting) {
    receiveEvent andThen {
      _ =>
        sendTextResponse("Sorry, I can't answer. DefaultTopic")
        stay()
    }
  }
  //override def setIntentDetector(intentDetector : ActorRef = null): Unit = {}

  initialize()
}

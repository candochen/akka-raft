package raft

import scala.concurrent.duration._
import java.util.Date
import scala.Long

object TimeUtil{

  def isTimeout(savedTime : Long, duration : Duration) : Boolean = {
    val timeDuration = System.currentTimeMillis() - savedTime 
    timeDuration > duration.toMillis
  }
}
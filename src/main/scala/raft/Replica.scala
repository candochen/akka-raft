package raft

import akka.actor._
import akka.event.Logging
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global   
import scala.Nothing
import scala.concurrent.{Await, Future}

import rx.lang.scala.Observable
import com.typesafe.config.ConfigFactory

import scala.collection.convert.wrapAll._

import scala.util.{Try, Success, Failure}

import java.util.{Timer, TimerTask, Date}


trait Message
case class VoteRequestMsg(term : Int, candidateId : Int, 
    lastLogIndex : Int, lastLogTerm : Int) extends Message
    
case class VoteResultMsg(term : Int, voteGranted : Boolean) extends Message

case class AppendEntryMsg(leaderTerm : Int, leaderId : Int, 
    prevLogIndex : Int, prevLogTerm : Int, entries : Array[String],
    leaderCommit : Int) extends Message

//append entry 
case class AppendEntriesRsMsg(term : Int, success : Int) extends Message

case object HeartBeatsMsg extends Message
case object LeaderLivenessMsg extends Message
case class  NotLeaderAnyMoreMsg(leaderTerm : Int, leaderId : Int) extends Message


trait Role
case object Follower extends Role
case object Candidate extends Role
case object Leader extends Role

object NodeServer{
  def main(args: Array[String]) {
    
    val config = ConfigFactory.load()
    val systemName = config.getString("raft.SystemName")
    //create an actor system with that configuration
    val system = ActorSystem(systemName , config)
    val nodeId = config.getString("raft.NodeId")
    //create a remote actor from actorSystem
    val remote = system.actorOf(Props[Node], name="Actor"+nodeId)
    
    println(remote + "remote is ready")
  }
}


//Client 直接连 Leader
class Node extends Actor {
  
  //The below values are loaded since server started
  var leaderTimeout : Duration  = Config.getLeaderTimeout
  //The above values are loaded since server started
  
  //Persistent state on all servers
  var currentTerm : Int = 0
  var leaderId  = 0
  var log : Iterable[String] = null
  var nodeId : String = Config.getNodeId
  var nodeIds : Iterable[String] = Config.getNodeIds
  var lastLeaderPingTime : Long = 0L
  
  var prefix = Config.getPrefix + "%s@"+ Config.getIp + ":" 
  
  //Volatile state on all servers
  var commitIndex : Int = 0
  var lastApplied : Int = 0
  var votedCount : Int = 0
  
  /**
   * The initial state is follower.
   * If a follower receives no communication, it becomes a candidate and initiates an election.
   * A candidate that receives votes from a majority of the full cluster becomes the new leader. 
   * Leaders typically operate until they fail.
   */ 
  var role : Role = null
  
  //Volatile state on leaders
  var nextIndex : List[String] = null
  var matchIndex : List[String] = null
  
  override def preStart(): Unit = { 
    
    currentTerm = NodeState.getCurrentTerm
    leaderId  = NodeState.getLeaderId
    lastLeaderPingTime  = NodeState.lastLeaderPingTime

    //for the first time, each actor is a candidate
    if (currentTerm == 0) {
    	role = Candidate
    }else{
    	role = NodeState.getRole
    }
    
    role match {
      case Leader => self ! HeartBeatsMsg
      case Candidate => {
        println("I am a candidate, my node ID is " + nodeId)
        runForLeader
      }
      case Follower => {
        println("I am a follower, my node ID is " + nodeId  + " now I am going to check leader")
        val timer = new Timer
        val timerTask = new TimerTask{ override def run{self ! LeaderLivenessMsg}}
    	timer.schedule(timerTask, 10000)
      }
    }
  }
  
   //run for the leader
  def runForLeader = {
    //Only candidates can run for a leader
    if (role == Candidate) {
      
    	//Wait for a random timeout to run for the leader
    	val runForLeaderTimeout = Config.randomTimeout
        Thread.sleep(runForLeaderTimeout)
        
    	println("run for leader...")
    	//increase the current term then run for leader  
	    //broadcast(new VoteRequestMsg(currentTerm + 1, nodeId, lastApplied, commitIndex))
	    val msg = new VoteRequestMsg(currentTerm + 1, nodeId.toInt, lastApplied, commitIndex)
	    
	    implicit val timeout = new Timeout(10 seconds)
	    
	    val futures = for {
	      nodeId <- nodeIds
	    } yield {
	      val remoteActor = actorSelection(nodeId)
	      //send synchronous message to the remote actors
	      //to obtain the voting message from them
	      
	      remoteActor ? msg
	    }
	    
	    //The voting results from all servers
	    val results  = for {
	      f <- futures
	    } yield Try(Await.result(f, timeout.duration ).asInstanceOf[VoteResultMsg])
	    
	    println("after time out....")
	    val votedCount = results.filter(
	    		x => x match {
	    		  case Success(v) =>
	    		    println("I have one vote")
	    		  	v.voteGranted 
	    		  case Failure(e) =>
	    		    //If any failure exists then don't count in
	    		    //the vote
	    		    e.printStackTrace()
	    		    false
	    		}
	    ).size
	    
	    
	    println("How many votes I got: " + votedCount)
	    println("How many nodes " + nodeIds.size )
	    
	    //If the candidate obtain the majority votes, then this candidate becomes the leader
	    if (votedCount != 0 && votedCount >= (nodeIds.size / 2)) {
	      println("I've gotten the majority vote")
	      role = Leader
	      leaderId = nodeId.toInt
	      currentTerm = currentTerm + 1
	      NodeState.updateValue("CurrentTerm", currentTerm)
	  	  NodeState.updateValue("LeaderId", leaderId)
	  	  NodeState.updateValue("RoleId", 1)
	  	  NodeState.updateAll
	      //If the candidate is become leader, then doing heart-beats
	      self ! HeartBeatsMsg
	    }else {
	      //Turn the role to follower and wait for heart-beats from the leader
	      role = Follower
	      //Send leader liveness checking message
	      self ! LeaderLivenessMsg
	    }
    }
  }
  
  def checkLeaderLiveness = {
    println("my role is " + role + " I am checking the liveness of Leader")
    if (role == Follower && TimeUtil.isTimeout(lastLeaderPingTime, leaderTimeout )) {
      println("It seems the leader is down, I am now become candidate to run for the leader")
      role = Candidate
      runForLeader
    }
  }
  
  
  def actorSelection(nodeId : String) = {     
    val remoteNodeUrl = prefix.format(nodeId) + nodeId + "/user/Actor"+nodeId
    println("Select node : " + remoteNodeUrl)
    context.actorSelection(remoteNodeUrl)
  }
  
  //Send message to all actors asynchronously
  def broadcast(msg : Message) = {
    nodeIds.map( nodeId => {
    		Future {
    	      val candidateActor = actorSelection(nodeId)
    		  candidateActor ! msg
    		}
    	} 
    )
  }

  /**
   *  broad heart-beats message to maintain the authority periodically
   */
  def heartbeats() = {
    //Leaders send periodic heart-beats(AppendEntries RPCs that carry no log entries) to all
    //followers in order to maintain their authority
    println("I'm the leader, I am doing heartbeats...")
    broadcast(new AppendEntryMsg(currentTerm, nodeId.toInt, 0 , 0, null, 0))
  }
  
  def receive = {
    case HeartBeatsMsg => {
        //Leader to send heart-beats
    	if (role == Leader){
    		heartbeats()
    		val heartBeatsDuration = Config.getHeartbeatsDuration
    		val cancellable = context.system.scheduler.scheduleOnce(heartBeatsDuration, self, HeartBeatsMsg)
    	}
    }
    
    case LeaderLivenessMsg =>{
    	  //Follower to check leader liveness
    	  println("received leader liveness message" + new Date())
    	  val leaderLivenessDuration = Config.getCheckLeaderLivenessPeriod
    	  if (role == Follower) {
    		 checkLeaderLiveness
    		 val timer = new Timer
    		 val timerTask = new TimerTask{ override def run{self ! LeaderLivenessMsg}}
    		 timer.schedule(timerTask, 10000)
    	  }
    }
    
    case VoteRequestMsg(term, candidateId, lastLogIndexId, lastLogTerm) => {
    	println("Receiving vote request...")
    	val candidateActor = actorSelection(candidateId.toString)
    	
    	println("Request Term " + term + " Current Term " + currentTerm)
    	
    	if (term > currentTerm) {
    	  println("I vote " + candidateActor + " as the leader")
    	      	  
    	  sender ! VoteResultMsg(term, true)
    	  
    	  leaderId = candidateId
    	  //If it is not the same node, turn the candidate to follower
    	  if (nodeId != candidateId){
    		  role  = Follower
    		  //In case admit the request to become the leader
    		  //then check for the liveness of the leader
    		  
    		  val timer = new Timer
    		  val timerTask = new TimerTask{ override def run{self ! LeaderLivenessMsg}}
    		  timer.schedule(timerTask, 10000)
    	  }
    	  
    	  //Update the current term
    	  NodeState.updateValue("CurrentTerm", term+1)
    	  NodeState.updateValue("RoleId", 2) //become follower
    	  NodeState.updateValue("LeaderId", leaderId)
    	  NodeState.updateAll
    	  
    	  println("update the node states")
    	  
    	}else {
    	  sender ! VoteResultMsg(currentTerm, false)
    	}
    }
  	case AppendEntryMsg(leaderTerm, leaderId, prevLogIndex, prevLogTerm, entries,leaderCommit) =>{
  		println("Leader Term: " + leaderTerm + " Current Term: " + currentTerm)
  		println("Leader Id: " + leaderId)
  		
  		//This is the leader heart-beats message
  	  	if (leaderTerm >= currentTerm){
  	  		currentTerm = leaderTerm
  	  		lastLeaderPingTime = System.currentTimeMillis()
  	  		
  	  		//Receive heart-beats message, which means each actor should become follower
  	  		println("receiving heartbeats from leader, I am now a follower. My node id is " + nodeId + " " + new Date)
  	  		role = Follower
  	  		
  	  		NodeState.updateValue("CurrentTerm", leaderTerm)
  	  		NodeState.updateValue("LeaderId", leaderId)
  	  		NodeState.updateValue("LastLeaderPingTime", lastLeaderPingTime)
  	  		NodeState.updateValue("RoleId", 2)
  	  		NodeState.updateAll
  	  	}else if (leaderTerm < currentTerm){ //received a hearts-beat message from an ex-leader
  	  		sender ! NotLeaderAnyMoreMsg(currentTerm, leaderId)
  	  	}
  	}
  	case NotLeaderAnyMoreMsg(term, currentLeaderId) =>{
  		if (term > currentTerm){
  			println("I am not the leader any more, the new leader is " + leaderId)
  			lastLeaderPingTime = System.currentTimeMillis()
  			role = Follower
  			leaderId = currentLeaderId
	  		NodeState.updateValue("CurrentTerm", term)
	  	  	NodeState.updateValue("LeaderId", leaderId)
	  	  	NodeState.updateValue("LastLeaderPingTime", lastLeaderPingTime)
	  	  	NodeState.updateValue("RoleId", 2)
	  	  	NodeState.updateAll
	  	  	//Send leader liveness checking message
		    self ! LeaderLivenessMsg
  		}
  	}
  }
}
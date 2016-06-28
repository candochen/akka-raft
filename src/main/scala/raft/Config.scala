package raft

import scala.concurrent.duration._
import java.util.Random
import scala.io.Source
import scala.util.Try
import java.util.Properties
import java.io.FileInputStream
import java.io.File
import java.io.IOException
import com.typesafe.config.ConfigFactory


import scala.collection.convert.wrapAll._
import java.io.File

object Config{
	val config = ConfigFactory.load()
	    
	def getPrefix = {
		config.getString("raft.RemoteSystem") 
	}
	
	def getNodeIds   = {
	  val nodeIds = config.getStringList("raft.NodeIds")
	  nodeIds.toIterable
	}
	
	def getNodeId = {
	  config.getString("raft.NodeId")
	}
		
	def getHeartbeatsDuration = {
	  val heartBeatsDuration = config.getInt("raft.HeartBeatsDuration")
	  heartBeatsDuration seconds
	}
	
	def getLeaderTimeout : Duration = {
	  val leaderTimeout = config.getInt("raft.LeaderTimeout")
	  leaderTimeout seconds
	}
	
	def getCheckLeaderLivenessPeriod = {
	  val checkLeaderLivenessPeriod =
	    config.getInt("raft.CheckLeaderLivenessPeriod")
	  checkLeaderLivenessPeriod seconds
	}
	
	def randomTimeout = {
	  val rand = new Random().nextInt(150) + 150
	  rand
	}
	
	def getIp = {
	  "127.0.0.1"
	}
}
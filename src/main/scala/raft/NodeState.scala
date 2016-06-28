package raft

import java.util.Properties
import java.io.InputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.io.FileOutputStream
import java.io.File

object NodeState{
  val nodeStateFile =  getClass.getClassLoader.getResource("nodeState.properties").getFile
  
  val prop = new Properties
  val fis = new FileInputStream(nodeStateFile)

  //If there is errors, let is be
  prop.load(new FileInputStream(nodeStateFile))
   
  def getCurrentTerm = {
    val currentTerm = prop.getProperty("CurrentTerm")
    println(currentTerm)
    currentTerm.toInt
  }
  
  def getLeaderId = {
    val leaderId = prop.getProperty("LeaderId")
    leaderId.toInt
  }
  
  def getRole: Role = {
    val roleId = prop.getProperty("RoleId")
    if ("1".equals(roleId)) Leader
    else if("2".equals(roleId)) Follower
    else Candidate
  }
  
  //get last leader ping time
  def lastLeaderPingTime : Long = {
    val lastLeaderPingTime = prop.getProperty("LastLeaderPingTime")
    lastLeaderPingTime.toLong
  }
  
  def updateValue(key: String, value: Any) = {
    prop.put(key, value.toString)
  }
  
  def updateAll = {
    val fos = new FileOutputStream(nodeStateFile)
    prop.store(fos, null)
  }
}
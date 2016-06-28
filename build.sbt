
name := "paxos"

version := "1.0"

scalaVersion := "2.11.4"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"


libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.7"

libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.3.7"
  
libraryDependencies += "io.reactivex" %% "rxscala" % "0.26.2"

libraryDependencies += "net.liftweb" %% "lift-json" % "2.6.3"

libraryDependencies += "com.enragedginger" %% "akka-quartz-scheduler" % "1.5.0-akka-2.4.x"
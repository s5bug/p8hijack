lazy val root = (project in file(".")).settings(
  organization := "tf.bug",
  name := "p8hijack",
  version := "0.1.0",
  scalaVersion := "2.13.11",
  libraryDependencies ++= Seq(
    "com.monovore" %% "decline" % "2.4.1",
    "org.seleniumhq.selenium" % "selenium-java" % "4.9.1",
    "com.outr" %% "scribe-slf4j" % "3.11.5",
  )
)

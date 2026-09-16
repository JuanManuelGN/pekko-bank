lazy val pekkoHttpVersion = "1.4.0"
lazy val pekkoVersion     = "1.4.0"
lazy val circeVersion     = "0.14.9" // Versión actualizada compatible con Scala 3

// version de sbt 2.0.8 si deja de funcionar pasar a 1.10.2
// Run in a separate JVM
fork := true

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "es",
      scalaVersion    := "3.8.4"
    )),
    name := "mini-bank",
    libraryDependencies ++= Seq(
      // --- CORE & HTTP ---
      "org.apache.pekko" %% "pekko-http"                % pekkoHttpVersion,
      "org.apache.pekko" %% "pekko-actor-typed"         % pekkoVersion,
      "org.apache.pekko" %% "pekko-stream"              % pekkoVersion,
      
      // --- PERSISTENCIA (Añadido del original) ---
      "org.apache.pekko" %% "pekko-persistence-typed"   % pekkoVersion,
      "org.apache.pekko" %% "pekko-persistence-cassandra" % "1.1.0",
      "com.datastax.oss" %  "java-driver-core"          % "4.17.0",
      
      // --- JSON (Circe para Scala 3) ---
      "io.circe"         %% "circe-core"                % circeVersion,
      "io.circe"         %% "circe-generic"             % circeVersion,
      "io.circe"         %% "circe-parser"              % circeVersion,
      
      // El reemplazo directo de 'de.heikoseeberger' para Pekko HTTP
      "com.github.pjfanning" %% "pekko-http-circe"      % "3.0.0",
      
      // --- LOGGING ---
      "ch.qos.logback"   % "logback-classic"            % "1.3.15",
      
      // --- TESTING ---
      "org.apache.pekko" %% "pekko-http-testkit"        % pekkoHttpVersion % Test,
      "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion     % Test,
      "org.scalatest"    %% "scalatest"                 % "3.2.19"         % Test
    ),
    // ESTO FUERZA A SBT A EVICIONAR LA VERSIÓN 1.1.3 QUE PIDE CASSANDRA
    dependencyOverrides ++= Seq(
      "org.apache.pekko" %% "pekko-cluster"       % pekkoVersion,
      "org.apache.pekko" %% "pekko-cluster-tools" % pekkoVersion,
      "org.apache.pekko" %% "pekko-coordination"  % pekkoVersion
    )
  )

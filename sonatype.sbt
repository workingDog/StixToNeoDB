
pomExtra := {
  <scm>
    <url>https://github.com/workingDog/StixToNeoDB</url>
    <connection>scm:git:git@github.com:workingDog/StixToNeoDB.git</connection>
  </scm>
    <developers>
      <developer>
        <id>workingDog</id>
        <name>Ringo Wathelet</name>
        <url>https://github.com/workingDog</url>
      </developer>
    </developers>
}

pomIncludeRepository := { _ => false }

publishMavenStyle := true

publishArtifact in Test := false

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

sonatypeProfileName := "com.github.workingDog"
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseTagName := (version in ThisBuild).value

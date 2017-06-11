
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

// Release settings
sonatypeProfileName := "com.github.workingDog"
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseCrossBuild := true
releaseTagName := (version in ThisBuild).value

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  ReleaseStep(action = Command.process("publishSigned", _)),
  releaseStepCommand("sonatypeRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)


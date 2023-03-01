def call(String applicationName, String buildStatus, String revision) {

  def currentBuildStatus = buildStatus ?: currentBuild.currentResult

  def payload = """\
  { \
    "application_name": "${applicationName}", \
    "build_url": "${env.BUILD_URL}", \
    "revision": "${revision}", \
    "build_status" : "${currentBuildStatus}" \
  } \
  """.stripIndent()

  echo "Sending the following payload to Tugboat to update Trello Card. \n ${payload}"

  tugboatRequest(
    endpoint: "jenkins/update-deployment",
    payload: payload,
    label: "Updating Tugboat with build status",
    type: "POST",
  )
}

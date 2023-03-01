def call(String auditType, String slackChannels, Exception potential_exception) {
  Boolean statusChanged = currentBuild.previousBuild == null || currentBuild.currentResult != currentBuild.previousBuild.result
  Boolean isPullRequest = env.CHANGE_ID != null
  if (slackChannels?.trim() && statusChanged && !isPullRequest) {
    String color = (potential_exception != null) ? "warning" : "good"
    String message = (potential_exception != null) ?
      "${auditType} Audit for ${env.JOB_NAME} has failed! (<${env.BUILD_URL}|Open>)" :
      "${auditType} Audit for ${env.JOB_NAME} has passed! (<${env.BUILD_URL}|Open>)"

    slackSend color: color, message: message, channel: slackChannels
  }
}

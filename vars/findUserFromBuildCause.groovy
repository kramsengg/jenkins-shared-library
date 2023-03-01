def call() {
  def causes
  causes = currentBuild.getBuildCauses('org.jenkinsci.plugins.github_branch_source.GitHubSenderCause')
  if (!causes.isEmpty()) {
    return causes[0].login
  }
  if (!currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause').isEmpty()) {
    // cron scheduled jobs do not have a user associated to the build cause
    // instead of using an "Anonymous" user, defer everything to the Jenkins user
    return "jenkins"
  }
  causes = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
  if (!causes.isEmpty()) {
    return causes[0].userId
  }
  throw new Exception("Could not determine a user to be designated as the deployer")
}

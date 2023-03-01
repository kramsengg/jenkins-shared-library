import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call (Map options = [:], Closure block) {
  def DEFAULT_TIMEOUT_AMOUNT = 2
  def DEFAULT_TIMEOUT_UNIT = 'HOURS'

  script {
    def timeout_amount = env.cd_input_timeout ?: (options.time ?: DEFAULT_TIMEOUT_AMOUNT)
    def timeout_unit = env.cd_input_timeout_unit ?: (options.unit ?: DEFAULT_TIMEOUT_UNIT)

    try {
      timeout(time: timeout_amount, unit: timeout_unit) {
        block()
      }
    } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException err) {
      // try to do something that requires a worker
      try {
        sh "echo \"\" > /dev/null"
        // if we get here, that means this build was aborted or closed off for some other reason that we don't care about
      } catch (eof) {
        if (eof.getMessage() ==~ /Unable to create live FilePath for(.*i-.*)/) {
          def agent_removed_message = "<${env.RUN_DISPLAY_URL}|Job=${env.JOB_NAME}, Build Number=${env.BUILD_NUMBER}> failed because the underlying worker was unexpectedly terminated. This build has been automatically triggered for re-run."
          echo agent_removed_message
          if (env.SLACK_CHANNEL) {
            slackSend(channel: env.SLACK_CHANNEL, message: agent_removed_message)
          }
          build job: env.JOB_NAME, wait: false
        } else {
          throw eof
        }
      }
      throw err
    }
  }
}

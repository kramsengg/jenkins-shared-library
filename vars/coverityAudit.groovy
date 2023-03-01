def call(Map args=[:]) {
  Boolean blockPipeline = args.containsKey('blockPipeline') &&
    args.blockPipeline != null &&
    args.blockPipeline
  String slackChannels = (args.slackChannel || args.slackChannels) ?: ""
  def potential_exception

  script {
    try {
      String applicationName = (args.containsKey('applicationName') && args.applicationName != null) ?
        args.applicationName :
        ''
      String coverityCommand = (args.containsKey('coverityCommand') && args.coverityCommand != null) ?
        args.coverityCommand :
        "drake coverity_scan"

      if (applicationName?.trim()) {
        echo "Missing applicationName arg for coverity audit."
      }

      env.STREAM_NAME = "${applicationName}_${env.BRANCH_NAME}"
      sh coverityCommand
    }
    catch(exception) {
      potential_exception = exception
    }
    finally {
      notifyAuditResults(
        'Coverity',
        slackChannels,
        potential_exception
      )

      if (potential_exception != null && blockPipeline) {
        throw potential_exception
      }
    }
  }
}

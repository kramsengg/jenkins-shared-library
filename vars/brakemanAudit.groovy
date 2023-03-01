def call(Map args=[:]) {
  Boolean blockPipeline = args.containsKey('blockPipeline') &&
    args.blockPipeline != null &&
    args.blockPipeline
  String slackChannels = (args.slackChannel || args.slackChannels) ?: ""
  def potential_exception

  script {
    try {
      env.BRAKEMAN_VERSION = "4.5.1"
      sh "docker pull dockerhub.braintree.tools/bt/brakeman:${env.BRAKEMAN_VERSION}"
      sh """
      docker run \
        --rm \
        --volume ${env.WORKSPACE}:/source \
        dockerhub.braintree.tools/bt/brakeman:${env.BRAKEMAN_VERSION} \
        brakeman -w2 -o /dev/stdout -o /source/brakeman-output.json /source
      """
    }
    catch(exception) {
      potential_exception = exception
    }
    finally {
      publishBrakeman 'brakeman-output.json'

      notifyAuditResults(
        'Brakeman',
        slackChannels,
        potential_exception
      )

      if (potential_exception != null && blockPipeline) {
        throw potential_exception
      }
    }
  }
}

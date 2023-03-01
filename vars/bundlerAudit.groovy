def call(Map args=[:]) {
  Boolean blockPipeline = args.containsKey('blockPipeline') &&
    args.blockPipeline != null &&
    args.blockPipeline
  String slackChannels = (args.slackChannel || args.slackChannels) ?: ""
  def potential_exception

  script {
    try {
      sh "docker pull dockerhub.braintree.tools/bt/bundler-audit:main"

      sh """
      docker run \
        --rm \
        --volume ${env.WORKSPACE}:/source \
        --workdir /source \
        dockerhub.braintree.tools/bt/bundler-audit:main \
        bash -c 'unset BUNDLE_APP_CONFIG && /home/bt/bin/bundler-audit check --update'
      """

      sh """
      docker ps --all --quiet | \
        xargs --no-run-if-empty docker rm --force --volumes
      """
    }
    catch(exception) {
      potential_exception = exception
    }
    finally {
      notifyAuditResults(
        'Bundler',
        slackChannels,
        potential_exception
      )

      if (potential_exception != null && blockPipeline) {
        throw potential_exception
      }
    }
  }
}

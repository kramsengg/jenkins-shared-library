def call(Map args=[:]) {
  String slackChannels = "#auto-security-test"
  def potential_exception

  script {
    try {
      sh "docker pull dockerhub.braintree.tools/bt/polaris:current"

      def responseCode = withCredentials([string(credentialsId: "POLARIS_ACCESS_TOKEN", variable: "POLARIS_ACCESS_TOKEN")]) {
      
        sh """
        docker run \
          --rm \
          -e POLARIS_ACCESS_TOKEN \
          --volume ${env.WORKSPACE}:/source \
          --workdir /source \
          dockerhub.braintree.tools/bt/polaris:current \
          bash -c 'polaris analyze'
        """
      }
    }
    catch(exception) {
      potential_exception = exception
    }
    finally {
      notifyAuditResults(
        'Polaris',
        slackChannels,
        potential_exception
      )

      if (potential_exception != null) {
        throw potential_exception
      }
    }
  }
}

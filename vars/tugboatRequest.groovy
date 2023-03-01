def call(Map options = [:]){
  def endpoint = options.get("endpoint")
  def label = options.get("label")
  def type = options.get("type", "POST")

  def curlOptions = ""
  if (options.containsKey("payload")){
    curlOptions += "--data '${options.get("payload")}' "
  }

  def tugboatBaseUrl = "https://tugboat.braintree.tools"
  if (env.JENKINS_URL == "https://jenkinsqa.braintree.tools/") {
    tugboatBaseUrl = "https://tugboat-qa-us-east-2.dev.braintree-api.com"
  }

  def CURL_OUTPUT_LOCATION = "/tmp/tugboat_curl_output"

  def aws_cli_image = "dockerhub.braintree.tools/bt/awscli:1.16.112-stretch"
  dockerPull(aws_cli_image, "Ensuring latest release of ${aws_cli_image}")

  def responseCode = withCredentials([
    [$class: "AmazonWebServicesCredentialsBinding", credentialsId: "jenkins-client-key-and-cert"]
  ]) {
    sh(
      script: "bash -o pipefail -c \"${dockerCommand(aws_cli_image)}\"",
      returnStdout: true,
      label: "Read Jenkins client cert and private key from AWS Secretsmanager",
    )
    sh (
      script: """curl -k\
      --silent --show-error\
      --write-out "%{http_code}"\
      --key "${env.WORKSPACE}/jenkins.client.key" \
      --cert "${env.WORKSPACE}/jenkins.client.crt" \
      --output ${CURL_OUTPUT_LOCATION} \
      --header "Content-Type: application/json"\
      --request ${type}\
      ${curlOptions} \
      ${tugboatBaseUrl}/${endpoint}""".stripIndent(),
      returnStdout: true,
      label: label,
    )
  }

  def responseBody = sh (
      script: "cat ${CURL_OUTPUT_LOCATION}",
      returnStdout: true,
      label: "Retrieving response body",
  )

  if (responseCode.getAt(0) == "2") {
    echo "Tugboat request succeeded!"
    return responseBody

  } else {
    echo "Tugboat request failed!\n${responseBody}"
    throw new Exception("Received an error from Tugboat")
  }
}

def dockerPull(image, label) {
  sh(script: "docker pull ${image}", label: label)
}

def aws_sm_command(secret_id) {
  return "aws secretsmanager get-secret-value --secret-id '${secret_id}' --region us-east-2 | jq -r '.SecretString'"
}

def dockerCommand(image) {
  String client_cert_aws_command
  String client_key_aws_command

  if (env.JENKINS_URL.contains("jenkinsqa")) {
    client_cert_aws_command = aws_sm_command("jenkins/ssl/certs/jenkins.jenkins-sand.braintree-api.com_client.bundle.crt")
    client_key_aws_command = aws_sm_command("jenkins/ssl/private/jenkins.jenkins-sand.braintree-api.com_client.key")
  } else {
    client_cert_aws_command = aws_sm_command("jenkins/ssl/certs/jenkins.jenkins-prod.braintree-api.com_client.bundle.crt")
    client_key_aws_command = aws_sm_command("jenkins/ssl/private/jenkins.jenkins-prod.braintree-api.com_client.key")
  }

  return "docker run --rm " +
    "-e AWS_ACCESS_KEY_ID " +
    "-e AWS_SECRET_ACCESS_KEY " +
    "-v ${env.WORKSPACE}:/app/workspace " +
    "${image} " +
    "bash -o pipefail -c \\\"" +
    "${client_cert_aws_command} > /app/workspace/jenkins.client.crt && " +
    "${client_key_aws_command} > /app/workspace/jenkins.client.key" +
    "\\\""
}

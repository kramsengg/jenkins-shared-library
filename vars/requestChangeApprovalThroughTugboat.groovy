def call (Map options = [:]) {
  if (env.CM_PLATFORM == "servicenow") {
    requestChangeApprovalThroughServiceNow(options)
    return
  }

  script {
    options["manuallySetRollback"] = options.get("manuallySetRollback", false)

    String rollbackRevision = rollbackRevision(options)

    echo "Creating a Jenkins Hook for Tugboat Response"
    def hook = registerWebhook()
    def hookURL = hook.getURL()

    startTugboatDeploy(hookURL, rollbackRevision, options)

    echo "Waiting for a Tugboat response to hook ${hookURL}"
    def data = waitForWebhook(hook)

    def jsonParsed
    try {
      jsonParsed = readJSON(text: data)
    }
    catch(exception) {
      echo "There was an unexpected issue with the Jenkins webhook ${hookURL}. Raw response: ${data}"
      throw new Exception(exception)
    }

    echo "Received response: ${jsonParsed.status}"

    def tugboatResponse = jsonParsed.status.toLowerCase()
    if (tugboatResponse != "approved") {
      String tugboatErrorMessage = jsonParsed.error ? "Error: ${jsonParsed.error}. ": ""
      String errorMsg = "Received a ${tugboatResponse} response from Tugboat. ${tugboatErrorMessage}Aborting..."
      echo errorMsg
      throw new Exception(errorMsg)
    }
    echo "The Tugboat deploy has been approved. Continuing to deployment"
  }
}

def startTugboatDeploy (String hookURL, String rollbackRevision, Map options) {
  if (!options.containsKey("revision")) {
    String exception = """
      required named parameter `revision`.
      If the application uses tag to track version, use env.JOB_BASE_NAME.
      If the application uses commit shas to track versions, use checkout(scm).GIT_COMMIT.
      """

    echo exception
    throw new Exception(exception)
  }
  def scmVars = checkout scm

  def applicationName = options.applicationName ?: findApplicationName()
  def deployerUsername = findUserFromBuildCause()

  if (deployerUsername == "jenkins") {
    // get rid of this entirely once DTBTDEVTLS-439 is played
    String exception = """
      It appears this is a build triggered by cron, which means Tugboat will not be able to derive a user to associate as its deployer. Are you sure you want to be triggering deploys on a cron schedule like this? You'll need to restart the pipeline from this stage so Tugboat can infer that you are the deployer.
    """
    echo exception
    throw new Exception(exception)
  }
  def revision = options.get("revision")

  if (applicationName && !env.TUGBOAT_APPLICATION_NAME) {
    env.TUGBOAT_APPLICATION_NAME = applicationName
  }

  if (revision && !env.TUGBOAT_REVISION) {
    env.TUGBOAT_REVISION = revision
  }

  String deploymentInfo = """
  Requesting Tugboat to start a deploy for
  Application: "${applicationName}"
  Version: "${revision}"
  """

  if (rollbackRevision.length() > 0) {
    deploymentInfo += "\nCurrent Version: ${rollbackRevision}"
  }
  echo deploymentInfo

  def payload = """\
  { \
    "application_name" : "${applicationName}", \
    "ghe_name" :  "${deployerUsername}", \
    "revision" : "${revision}", \
    "current_revision": "${rollbackRevision}", \
    "jenkins_hook" :  "${hookURL}", \
    "build_url":  "${env.BUILD_URL}" \
  } \
  """.stripIndent()
  echo "Sending the following payload to jenkins \n${payload}"

  tugboatRequest(
    endpoint: "jenkins",
    payload: payload,
    label: "Send Payload to Start Tugboat Deployment",
    type: "POST",
  )
}

def rollbackRevision (Map options) {
  if (options.rollbackRevision != null) {
    return options.rollbackRevision
  }

  if (!options.manuallySetRollback) {
    return ""
  }

  if (!options.containsKey('channel')) {
    String exception = """
    required named parameter `channel`
    If the manuallySetRollback is set to true, channel must be provided for notification.
    """

    echo exception
    throw new Exception(exception)
  }

  if ((env.JENKINS_URL == "https://ci.braintree.tools/") || (env.JENKINS_URL == "https://jenkins.jenkins-prod.braintree-api.com/")) {
    String channel = options.get('channel')
    String slackHandle = options.get("slackHandle", "")
    String applicationName = options.applicationName ?: findApplicationName()
    String deployerUsername = findUserFromBuildCause()
    String message = """
    Please input rollback revision. ${slackHandle}
    (<${env.BUILD_URL}/input|Classic UI> | <${env.RUN_DISPLAY_URL}|Blue Ocean>)
    Repository: ${applicationName}
    Build Name: ${env.JOB_NAME}
    Deployer: ${deployerUsername}
    """

    slackSend(channel: channel, message: message)
  }

  return inputWithTimeout(
    message: "Rollback Revision",
    id: "RollbackRevision",
    parameters: [
      [
        $class: 'TextParameterDefinition',
        name: "rollback_revision",
        defaultValue: "",
        description: "Please provide rollback revision.",
      ],
    ]
  )
}

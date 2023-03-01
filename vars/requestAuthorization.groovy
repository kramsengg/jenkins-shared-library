import static org.braintree.DatadogMetricsUtil.*
import org.braintree.HistogramTimer

def call(Map args=[:]) {
  script {
    String repository = args.get("repository", findRepository())
    String revision = args.revision
    if (revision == null) {
      throw new Exception("required named parameter `revision`")
    }
    List<String> targets = args.targets
    if (targets == null) {
      throw new Exception("required named parameter `targets`")
    }
    String channel = args.channel
    if (channel != null && !(channel =~ /^#/)) {
      throw new Exception("malformed channel `${channel}`")
    }
    String handle = args.handle
    if (handle != null && !(handle =~ /^@/)) {
      throw new Exception("malformed handle `${handle}`")
    }
    if (channel == null) {
      channel = handle
      handle = null
    }
    String deployEnv = args.deployEnv
    if (deployEnv == null) {
      throw new Exception("required named parameter `deployEnv`")
    }

    dockerPull()
    def jpwEnv
    if (env.JENKINS_URL == "https://ci.braintree.tools/") {
      jpwEnv = revisionAllowed(targets[0], revision: revision) ? "production" : "prod-test"
    } else if (env.JENKINS_URL == "https://jenkins.jenkins-prod.braintree-api.com/") {
      jpwEnv = revisionAllowed(targets[0], revision: revision) ? "jenkins-prod" : "jenkins-prod-test"
    } else {
      jpwEnv = revisionAllowed(targets[0], revision: revision) ? "sand" : "sand-test"
    }

    // the input step plugin capitalizes the first character of the provided input...for some reason...
    String inputId = UUID.randomUUID().toString().capitalize()
    String inputUrl = "${env.BUILD_URL}input/${inputId}/submit"

    List<String> deployers = []
    targets.each { target ->
      deployers << whichDeployer(target)
    }
    deployers.unique()

    if (deployers.size() > 1) {
      throw new Exception("Jenkins Production Worker cannot deploy for Terraform and K8s in the same deployment at this time. Please only include targets of the same deployer.")
    }
    String deployer = deployers.first()

    String command = "bt-authorize --repository=\"${repository}\" --revision=\"${revision}\" --env=\"${jpwEnv}\" --deploy-env=\"${deployEnv}\" --deployer=\"${deployer}\""
    for (String target in targets) {
      command += " --target=\"${target}\""
    }

    command += " \"${inputUrl}\""

    if (channel != null) {
      String message = "[${deployEnv.toUpperCase()}] ${env.JOB_NAME} - #${env.BUILD_NUMBER} Authorization required.\n(<${env.BUILD_URL}/input|Review diff via Classic UI> | <${env.RUN_DISPLAY_URL}|Review diff via Blue Ocean>)\n\n"

      if (handle == null) {
        message += "Please run the following command on your local workstation.\n\n"
      } else {
        message += "${handle} please run the following command on your local workstation.\n\n"
      }

      message += "```\n${command}\n```\nGet help in #service-jenkins.\n\n"

      message += "Note: If you have never run `bt-authorize` commands, you will need to install `bt-authorize` on your local workstation. (<https://github.braintreeps.com/braintree/jenkins-production-worker#installing-bt-authorize|Installation link>)\n\n"

      slackSend(
        color: "#24b0d5",
        channel: channel,
        message: message,
      )
    }

    echo "Please run the following command on your local workstation. `${command}`"

    HistogramTimer timer = new HistogramTimer()
    timer.start()
    String raw = inputWithTimeout(
      message: "Authorization required",
      id: inputId,
      parameters: [
        [
          $class: 'TextParameterDefinition',
          name: "credentials_id_map",
          defaultValue: "",
          description: "Please run the following command on your local workstation. `${command}`",
        ],
      ],
    )

    timer.end()
    String[] tags = tags(env.JOB_NAME, env.BUILD_ID, targets: targets)

    def dataDogClient = statsDClient(tags: tags)
    dataDogClient.histogram("ci.auth.duration.seconds", timer.duration())
    
    def parsed = readJSON(text: raw)
    def credential_map = parsed
    def authorizer_maybe = ""
    // this is for backwards compatibility with bt-authorize versions <= 0.73.0
    if (parsed.authorizer) {
      authorizer_maybe = parsed.authorizer
      credential_map = parsed.credential_map
    }

    if (channel != null) {
      env.AUTHORIZER = authorizer_maybe
      String authorization_given_suffix = authorizer_maybe.empty ? "" : " by ${authorizer_maybe}"
      String successMessage = "[${deployEnv.toUpperCase()}] ${env.JOB_NAME} - #${env.BUILD_NUMBER} Authorization given${authorization_given_suffix}. (<${env.RUN_DISPLAY_URL}|Open>)"

      slackSend(
        color: "good",
        channel: channel,
        message: successMessage,
      )
    }

    return credential_map
  }
}

def dockerImage() {
  return "dockerhub.braintree.tools/bt/jenkins-production-worker-ci:release"
}

def dockerPull() {
  sh(script: "docker pull ${dockerImage()}", label: "Ensuring latest release of Jenkins Production Worker")
}

def dockerCommand(String command) {
  return "docker run " +
    "-v /var/run/docker.sock:/var/run/docker.sock " +
    "-v ${env.HOME}/.docker/config.json:/root/.docker/config.json " +
    "-v ${env.WORKSPACE}:/root/workspace " +
    "${dockerImage()} " +
    "${command}"
}

def revisionAllowed(options, target) {
  return sh(
    script: dockerCommand(deployCommand(options + [check_revision_allowed: true], target)),
    returnStatus: true,
    label: "Check if revision is on an allowed branch",
  ) == 0
}

def deployCommand(Map options, String target) {
  def command = "bt-deploy"
  if (options.revision) {
    command += " --revision=\"${options.revision}\""
  }
  if (options.dryRun) {
    command += " --dry-run"
  }
  if (options.credentialsId) {
    command += " --credentials-id=\"${options.credentialsId}\""
  }
  if (options.check_revision_allowed) {
    command += " --check-revision-allowed"
  }
  if (options.whichDeployer) {
    command += " --which-deployer"
  }
  command += " \"${target}\""

  return command
}

def whichDeployer(String target) {
  return sh(
    script: "bash -o pipefail -c \"${dockerCommand(deployCommand([whichDeployer: true], target))} | tail -1 | awk '{print \$NF}'\"",
    returnStdout: true,
    label: "Check which deployer is for the specified target",
  ).trim()
}

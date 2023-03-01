def call(Map args = [:], String target) {
  script {
    String channel = args.channel
    if (channel != null && !(channel =~ /^#/)) {
      throw new Exception("malformed channel `${channel}`")
    }
    String deployEnv = args.deployEnv
    if (deployEnv == null) {
      throw new Exception("required named parameter `deployEnv` was missing")
    }
    String revision = args.revision
    if (revision == null) {
      throw new Exception("required named parameter `revision` was missing")
    }
    if (target == null) {
      throw new Exception("required named parameter `target` was missing")
    }

    def terraformCredentialsIdMap = requestAuthorization(
      revision: revision,
      targets: [target],
      channel: channel,
      deployEnv: deployEnv
    )
    def statusCode = btDeploy(
      target,
      revision: revision,
      dryRun: true,
      exitDiff: true,
      credentialsId: terraformCredentialsIdMap[target]
    )
    if(statusCode == 0) {
      echo "No diff was found in the target environment. Skipping apply because there are no changes."
      return
    }

    terraformCredentialsIdMap = requestAuthorization(
      revision: revision,
      targets: [target],
      channel: channel,
      deployEnv: deployEnv
    )
    btDeploy(
      target,
      revision: revision,
      credentialsId: terraformCredentialsIdMap[target],
      withServiceNow: env.CHG_TICKET ? true : false
    )
  }
}

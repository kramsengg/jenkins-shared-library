Jenkinsfile Shared Libraries
===

This is a repo for storing global shared libraries for common elements in a Jenkinsfile.

Any functions defined on the `master` branch of this repo are available in any
Jenkinsfile with a pipeline auto-generated within Jenkins folders

* [braintree](https://ci.braintree.tools/job/braintree/)
* [braintree_audit](https://ci.braintree.tools/job/braintree_audit/)

## Methods

### `brakemanAudit`

#### Signature
```groovy
brakemanAudit(Map args=[:])
```

#### Purpose
A static analysis tool for finding vulnerabilities in software written in Ruby.

#### Usage
Required args: `none`

Optional args:
  1. `slackChannel` - (String; default: "") channel to notify of audit results.
  2. `blockPipeline` - (Boolean; default: _false_) set this to **true** if you would like brakeman failures to fail the entire pipeline. When **false**, the Brakeman stage will appear green, but the slack channel will be notified of failures.

Note: If running `brakemanAudit` in parallel stages, please specify an agent.

---

### `polarisAudit`

#### Signature
```groovy
polarisAudit(Map args=[:])
```

#### Purpose
A static analysis tool for finding vulnerabilities in code. This is the successor to brakemanAudit

#### Usage
Required args: `none`

Note: If running `polarisAudit` in parallel stages, please specify an agent.

---

### `btShipIt`

#### Signature
```groovy
btShipIt(List<String> targets, revision: String, deployEnv: String, channel: String)
```

#### Purpose
Single method that performs the Continuous Delivery pattern of:
  1. (Sand/Prod only) Display a diff of the changes to be deployed.
  2. (Prod only) Request approval via Tugboat.
  3. (Sand/Prod only) Obtain credentials from the user to perform the deploy on their behalf.
  4. Deploy the application.

If this method is run from a **Pull Request build**, it will only provide a diff, and may only run against the Dev and QA environments.

#### Usage
Required args:
  1. `targets` - (String/List<String>) the predefined build targets to deploy. These map to both the targets defined in your repo's `bt-deploy.yaml` file, and their names in Tugboat.
  1. `revision` - (String) the git revision to build.
  1. `deployEnv` - (String) the predefined deploy env that the target is deploying to.
  1. `channel` - (String; default: `env.SLACK_CHANNEL`) The channel all notifications will be sent. Required for obtaining rollback revision in startTugboat, optional to receive notifications for authorization.

Optional args:
  1. `enableDiffConfirm` - (Boolean; default: true) true will require a deployer to acknowledge they have reviewed a supplied Kubernetes diff when deploying to Sandbox or Production.
  1. `skipSandDiffConfirm` - (Boolean; default: true) true will skip prompting the deployer to acknowledge a Kubernetes diff when deploying to Sandbox
  1. `repository` - (String; default: repository in `env.GIT_URL`) The git repository to request authorization for (e.g. `braintree/drake_example`).
  1. `handle` - (String) Slack user/group from which to request authorization and prompt for diff reviews (e.g. `@devtools-team`).
  1. `timeout_in` - (int; default: 45) The amount of time until a timeout will occur on the downstream `btDeploy` operation. Associated with the `timeout_unit` argument.
  1. `timeout_unit` - (String; default: "MINUTES") The time unit for the `timeout_in` argument before a timeout will occur on the downstream `btDeploy` operation. Supported values are: "SECONDS", "MINUTES", "HOURS", "DAYS".
  1. `rollbackRevision` - (String) if provided, this will set the rollback revision for the Tugboat card instead of relying on Tugboat to gather that information. This is useful when a Tugboat application does not have a version callback url but the running version can be determined within the Jenkinsfile.
  2. `deployNotifyChannel` - (String) The channel(s) that Jenkins will publish a deployment notification to. Multiple channels may be provided as a comma, semicolon, or space delimited string (i.e. `"#dev-announce,#team-channel,#auto-team-channel` or `"#dev-announce;#team-channel;#auto-team-channel` or `"#dev-announce #team-channel #auto-team-channel`).

ServiceNow Optional args:
  1. `templateName` - (String; default: BT Jenkins - Jenkins Normal Release Template) The name of the template to be used when creating a change request. If the `templateName` provided cannot be used or found by Jenkins, the deployer will be prompted to fail the pipleline or use the deployment-vehicle specific template, BT Jenkins - Normal Release Template.
  1. `changeTicketType` - (String; default: normal) type of change request to be created. Value can be either `normal` or `standard`, required field to skip approval on change requests when using a standard template.
  1. `risk` - (String; default: moderate) risk level of the deployement, value can be `low`, `moderate`, `high`. Note, this field is only used to find
  an app-specific template matching the naming convention of BT-Site_Component-Risk-Type. It does not override the risk field set in a given template.
---

### `btBuild`

#### Signature
```groovy
btBuild(List<String> targets, revision: String, imageTag: String, push: Boolean)
```

#### Purpose
Invoke [bt-build](https://github.braintreeps.com/braintree/jenkins-production-worker) on a production worker.

#### Usage
Required args:
  1. `targets` - (List<String>) the predefined build targets to build.
  1. `revision` - (String) the git revision to build.

Optional args:
  1. `imageTag` - (String; default: `revision`, as above) the tag to tag the built image with.
  1. `push` - (Boolean; default: _true_) whether to push the built images.

---

### `btDeploy`

#### Signature
```groovy
btDeploy(String target, revision: String, credentialsId: String, dryRun: Boolean, exitDiff: Boolean)
```

#### Purpose
Invoke [bt-deploy](https://github.braintreeps.com/braintree/jenkins-production-worker) on a production worker.

#### Usage
Return value: (Integer) Exit code returned by the invoked deploy/dry-run. The exit code determines the result of the deploy/dry-run:
- exit code 0 - Completed successfully. If this was a dry-run with `exitDiff: true`, this signals there was no diff.
- exit code 1 - An error was raised.
- exit code 2 - Completed successfully. If this was a dry-run with `exitDiff: true`, this signals there was a diff.

Required args:
  1. `target` - (String) the predefined deploy target to deploy.
  1. `revision` - (String) the git revision to deploy.

Required for kubernetes deploy and unnecessary for Terraform deploy:
1. `deployEnv` - (String) the predefined cosmos account that the target is deploying to.

Optional args:
  1. `credentialsId` - (String; default: null) the ID of pre-uploaded credentials to deploy with. **Required for deploys to Sandbox/Production.** Environments below Sandbox & Prod will use a Jenkins service account to perform the deploy.
  1. `defaultDiffInputValue` - (Boolean; default: false) if true, the check box when prompted to review Kubernetes/Terraform Dry Run's will be checkmarked.
  1. `dryRun` - (Boolean; default: false) will **not** apply deployment changes if `true`.
  1. `channel` - (String; default: `env.SLACK_CHANNEL`) **Required** when `dryRun` is true. The channel to notify of reviewing k8s diff.
  1. `exitDiff` - (Boolean; default: false) if true, exit with code 2 on finding a diff; only works with `dryRun`
  1. **(Deprecated; please use `withServiceNow` instead)** `withTugboat` - (Boolean; default: false) will coordinate the Deploy via the [Tugboat slackbot](https://github.braintreeps.com/braintree/tugboat/), which will manage the Trello Card for the deploy as it progresses, and obtain approval if your application is configured for it. Be sure to [configure your application](https://knowledge.braintree.tools/ci/tugboat/deployers_guide/#configure-your-application-on-tugboat) and [configure your user](https://github.braintreeps.com/braintree/knowledge/blob/f263a3a6132f46f03c056c48627830c560ecec9b/ci/tugboat/tugboat_user_guide.md#configuring-your-tugboat-user) with Tugboat when setting this to `true`.
  1. `withServiceNow` - (Boolean; default: false) will cause `btDeploy` to coordinate the creation, approval, and management of a change record in ServiceNow as the deployment progress. If approvals are required (i.e. a type Normal change request), `btDeploy` will await a webhook callback from ServiceNow notifying of any approval or rejections that may have been received via email, Slack (Unobot), or directly.
  1. `enableDiffConfirm` - (Boolean; default: true) true will require a deployer to acknowledge they have reviewed a supplied Kubernetes diff.
  1. `timeout_in` - (int; default: 45) The amount of time until a timeout will occur on the `btDeploy` operation. Associated with the `timeout_unit` argument.
  1. `timeout_unit` - (String; default: "MINUTES") The time unit for the `timeout_in` argument before a timeout will occur on the `btDeploy` operation. Supported values are: "SECONDS", "MINUTES", "HOURS", "DAYS".
  1. `deployNotifyChannel` - (String) The channel(s) that Jenkins will publish a deployment notification to. Multiple channels may be provided as a comma, semicolon, or space delimited string (i.e. `"#dev-announce,#team-channel,#auto-team-channel` or `"#dev-announce;#team-channel;#auto-team-channel` or `"#dev-announce #team-channel #auto-team-channel`).


Use the method `requestAuthorization` to facilitate retrieving `credentialsId` from a user. e.g

```groovy
script {
  def credentialsIdMap = requestAuthorization(revision: env.GIT_COMMIT, targets: ["drake_example"], deployEnv: "prod")
  btDeploy("drake_example", revision: env.GIT_COMMIT, credentialsId: credentialsIdMap["drake_example"])
}
```

---
### `requestChangeApprovalThroughServiceNow`

#### Signature
```groovy
requestChangeApprovalThroughServiceNow (Map options=[:])
```

#### Purpose
Automates the creation of change requests in ServiceNow for deployments through Jenkins. For `normal` change tickets, this step will request approval and pause the pipeline until it receives a response from ServiceNow. `standard` change tickets skip the approval process and automatically transistion to the `scheduled` stage.

See the ServiceNow FAQ documentation [here](https://github.braintreeps.com/braintree/knowledge/ci/service_now/service_now_faq.md) for more details and on how templates are selected by the helper method.

#### Usage
Optional args:
  1. `applicationName` - (String; default: repository in `env.GIT_URL`) the name of the application being deployed.
  1. `templateName` - (String; default: BT Jenkins - Jenkins Normal Release Template) The name of the template to be used when creating a change request. If the `templateName` provided cannot be used or found by Jenkins, the deployer will be prompted to fail the pipleline or use the deployment-vehicle specific template, BT Jenkins - Normal Release Template.
  1. `changeTicketType` - (String; default: normal) type of change request to be created. Value can be either `normal` or `standard`, required field to skip approval on change requests when using a standard template.
  1. `risk` - (String; default: moderate) risk level of the deployement, value can be `low`, `moderate`, `high`. Note, this field is only used to find an app-specific template matching the naming convention of BT-Site_Component-Risk-Type. It does not override the risk field set in a given template.
___

### `requestChangeApprovalThroughTugboat` (Deprecating)

#### Signature
```groovy
requestChangeApprovalThroughTugboat (Map options=[:])
```

#### Purpose
Starts a Tugboat deployment assuming the application and necessary users have been configured. If not, see [our deployers guide](https://github.braintreeps.com/braintree/tugboat/blob/main/docs/deployers_guide.md). This step will pause the pipeline until it receives a response from Tugboat.

If the app has been configured to not require approval (e.g. it is a low risk app and using Tugboat for bookkeeping), Tugboat will send a prompt containing a proceed button. The deployer should verify that the created Trello card is accurate and the click on `Proceed`. The pipeline will then resume to the next step/stage.

If the app has been configured to require approval, Tugboat will send a message containing the status of the builds attached to the latest commit as well as a button to `Request Approval` or `Cancel`. Cancelling the deployment through Tugboat at any point will result in the pipeline failing and exiting. The deployer should review the created Trello card and then click on `Request Approval`. Once approval has been granted, the pipeline will resume. Deployment rejections will not affect the pipeline and deployers can request for approval multiple times.

#### Usage
Required args:
  1. `revision` - (String) the revision to deploy
      1. If the application uses tags to track versions, use
      ```groovy
      requestChangeApprovalThroughTugboat(revision: env.JOB_BASE_NAME)
      ```
      1. If the application uses commit shas to track versions, use
      ```groovy
      requestChangeApprovalThroughTugboat(revision: checkout(scm).GIT_COMMIT)
      ```

Optional args:
  1. `applicationName` - (String; default: repository in `env.GIT_URL`) the name of the application on Tugboat if it is not the same as the repository name
  1. `handle` - (String; default: "") Slack user or group notified of input step included in the message requesting for a rollback revision
  1. `manuallySetRollback` - (Boolean) set to `true` if the application does not have a revision route and a rollback revision will have to specified by the deployer
      1. `channel` - (String) only needed when `manuallySetRollback` is true. This is the channel that will be notified when the pipeline is ready to receive a rollbackRevision to start a Tugboat deploy. Please do not set this to a channel that will be ignored.
  1. `rollbackRevision` - (String) if provided, this will set the rollback revision for the Tugboat card instead of relying on Tugboat to gather that information. This is useful when a Tugboat application does not have a version callback url but the running version can be determined within the Jenkinsfile.

---

### `startTugboat` (Deprecating)

#### Signature
```groovy
startTugboat(Map options=[:])
```

#### Purpose
Start a Tugboat deployment from Jenkins Pipeline to document the deployment process.  This is an alias of requestChangeApprovalThroughTugboat for easier understanding.

#### Usage
[Reference requestChangeApprovalThroughTugboat for Usage](#requestChangeApprovalThroughTugboat)

---

### `notifyTugboat` (Deprecating)

#### Signature
```groovy
// Update your post block at the end of your pipeline to contain the following `always` condition
post {
  always {
    notifyTugboat()
  }
}
```

#### Purpose
`notifyTugboat` will let Tugboat know how the build ended so that Tugboat can automatically move or archive cards. Tugboat will automatically label the card with the status, add a comment, add the stages of the pipeline (including which ones failed). If there is not a Trello card or a Tugboat deployment, then it will be a noop (e.g. on a PR pipeline). It is required to exist inside of an `always` post block.

#### Usage
Required args: None

Optional args: None

---

### `notifyServiceNow`

#### Signature
```groovy
// Update your post block at the end of your pipeline to contain the following `always` condition
post {
  always {
    notifyServiceNow()
  }
}
```

#### Purpose
`notifyServiceNow` updates the Change Request in ServiceNow with close notes based on the status of the pipeline and deployment. It also prompts for the post-deployemnt or rollback checklists based on the outcome of each deployment. It is required to exist inside of an `always` post block.

#### Usage
Required args: None

Optional args: None

---

### `createParallelStages`

#### Signature
```groovy
createParallelStages(List<String> elements) { element -> <closure to be executed> }
createParallelStages(Map elements) { element, args -> <closure to be executed> }
```

#### Purpose
`createParallelStages` will alleviate repeatable code and create parallel stages in a scripted step as a work around of Jenkins Declarative Syntax size limit.

#### Usage
Required args:
  1. (Map/List<String>) - a Map or a List such that a stage will be created per entry in the map. For a Map by default, the key will be passed as the first argument to the block. The value can be of any type, but ideally should be a map containing additional arguments for the block to process.
  1. (Block) - a closure containing a method to execute per stage
Optional keyword args:
  1. failFast - (Boolean; default: false) set to `true` if any stage fails in the parallel stages, the rest of the parallel stages will immediately exit. By default (i.e. default is `false`), when one stage fails, any stages in progress will continue running to completion.
  1. shallowClone - (Boolean; default: false) set to `true` to enable a shallow clone of the repo.
  1. shallowCloneDepth - (Integer; default: 1) determines how many commits back to clone.
  1. label - (String) allows the parallel stages to specify which Jenkins worker label to use when running the parallel stages.

```
def exampleMap = [
  // The keys will be provided as the first element and the value will be the second argument in the closure. For example, they could be different Kubernetes contexts  that the pipeline deploys to.
  // The value can be of any type including another map if additional args are needed for methods in the closure. For example, they could document that some contexts could be in Blue and require additional arguments.

  'us-west-2': [arg1: 'foo'],
]

createParallelStages(exampleParams, failFast: true) { region, args -> closureMethod(region, args)}

def exampleArray = [
  'us-east-1' // omit value if no args are needed to pass to the closure
]

createParallelStages(exampleArray, failFast: true) { region -> closureMethod(region)}
```

---

### `bundlerAudit`

#### Signature
```groovy
bundlerAudit(Map args=[:])
```

#### Purpose
Checks for vulnerable versions of gems in Gemfile.lock, insecure gem sources,
and prints advisory information.

#### Usage
Required args: `none`

Optional args:
  1. `slackChannel` - (String; default: "") channel to notify of audit results.
  1. `blockPipeline` - (Boolean; default: _false_) set this to **true** if you would like audit failures to fail the entire pipeline. When **false**, the step will appear green, but the slack channel will be notified of failures.

---

### `coverityAudit`

#### Signature
```groovy
coverityAudit(Map args=[:])
```

#### Purpose
Coverity Scan is a service by which Synopsys provides the results of analysis
on open source coding projects to open source code developers that have
registered their products with Coverity Scan.

#### Usage
Required args:
  1. `applicationName` - (String) The name of your application.

Optional args:
  1. `slackChannel` - (String; default: "") channel to notify of audit results.
  1. `blockPipeline` - (Boolean; default: _false_) set this to **true** if you would like audit failures to fail the entire pipeline. When **false**, the step will appear green, but the slack channel will be notified of failures.
  1. `coverityCommand` - (String; default: "drake coverity_scan") Command to run to invoke the coverity scan.

---

### `notifyAuditResults`

#### Signature
```groovy
notifyAuditResults(String auditType, String slackChannels, Exception potential_exception)
```

#### Purpose
Helper method used by methods `brakemanAudit`, `bundlerAudit`, and `coverityAudit`.

---

### `requestAuthorization`

#### Signature
```groovy
requestAuthorization(revision: String, targets: List<String>, repository: String, channel: String, handle: String)
```

#### Purpose
Requests that a deployer run `bt-authorize` to proceed with a deploy.

Required args:
  1. `revision` - (String) The git revision to request authorization for.
  1. `targets` - (List<String>) The deploy targets to request authorization for.
  1. `deployEnv` - (String) the predefined deploy env for which the target is being authorized.

Optional args:
  1. `repository` - (String; default: repository in `env.GIT_URL`) The git repository to request authorization for (e.g. `braintree/drake_example`).
  1. `handle` - (String; default: _null_) Slack user/group from which to request authorization (e.g. `@devtools-team`). Used to mention if a `channel` parameter is provided, as below.
  1. `channel` - (String; default: `handle`, as above) Slack channel in which to request authorization (e.g. `#auto-dev-tools`). If not provided, the user specified as `handle` is directly messaged.

---

### `s3Artifacts`

#### Signature
```groovy
s3Artifacts(Map args=[:])
```

#### Purpose
Ship container logs to `bt-jenkins-build-logs` bucket in S3.

#### Usage
Required args: `none`

Optional args:
  1. `sourceFile` - (String; default: `drake_container_logs-*.tgz`) A filename pattern. All filenames matching this pattern will be uploaded to an S3 bucket.

---

### `inputWithTimeout`

#### Signature
```groovy
inputWithTimeout(
  timeout_amount: 5,
  timeout_unit: "MINUTES",
  [any arguments that can be passed to the input step]
  )
```

#### Purpose
Invokes the input step but wraps the step with a timeout wrapper so that inputs do not wait indefinitely.

#### Usage
Required args:
  1. `inputWithTimeout` will pass its arguments to the input step. It accepts any [arguments for configuring input](https://jenkins.io/doc/pipeline/steps/pipeline-input-step/#-input-wait-for-interactive-input).

Optional Args:
`sourceFile` - (String; default: `drake_container_logs-*.tgz`) A filename pattern. All filenames matching this pattern will be uploaded to an S3 bucket.
  1. `timeout_amount` - (Integer, default: `2`) The amount of time to wait before aborting the pipeline. To set the amount globally for the pipeline, set the environment variable `cd_input_timeout`.
  2. `timeout_unit`- (String, default: `'HOURS'`) The unit of time to wait before timing out. Acceptable values are [`NANOSECONDS`, `MICROSECONDS`, `MILLISECONDS`, `SECONDS`, `MINUTES`, `HOURS`, `DAYS`]. To set the amount globally for the pipeline, set the environment variable `cd_input_timeout_unit`.

---

### `deployTerraform`

#### Signature
```groovy
deployTerraform(Map args = [:], String target)
```

#### Purpose
Performs a terraform plan and applies any changes.

#### Usage
Required args:
  1. `channel` - (String) The Slack channel to notify when reviewing a terraform plan diff.
  2. `deployEnv` - (String) The predefined Cosmos account that the target is deploying to.
  3. `revision` - (String) The git revision to deploy.
  4. `target` - (String) The predefined deploy target to deploy.

def call(Map args=[:]){
  String sourceFile = (args.containsKey('sourceFile') && args.sourceFile != null) ?
    args.sourceFile :
    "drake_container_logs-*.tgz"

  s3Upload(
    profileName: 'bt-jenkins-build-logs',
    entries: [
      [
        sourceFile      : sourceFile,
        bucket          : 'bt-jenkins-build-logs',
        selectedRegion  : 'us-east-1',
        managedArtifacts: true,
        uploadFromSlave : true,
      ]
    ],
    userMetadata: [],
    dontWaitForConcurrentBuildCompletion: true,
    consoleLogLevel: 'WARNING',
    pluginFailureResultConstraint: 'SUCCESS',
  )
}

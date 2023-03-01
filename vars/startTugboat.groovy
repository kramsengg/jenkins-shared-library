def call (Map options = [:]) {
  if (env.CM_PLATFORM == "servicenow") {
    echo "DEPRECATED: Switch `startTugboat` steps in Jenkinsfile to `requestChangeApprovalThroughServiceNow`"
    requestChangeApprovalThroughServiceNow(options)
  } else {
    requestChangeApprovalThroughTugboat(options)
  }
}

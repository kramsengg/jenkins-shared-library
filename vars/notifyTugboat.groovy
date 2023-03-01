def call() {
  if (env.CM_PLATFORM == "servicenow") {
    echo "DEPRECATED: `notifyTugboat` is deprecated. Please replace it with notifyServiceNow()"
    notifyServiceNow()
  } else {
    updateTugboat()
  }
}

def updateTugboat() {
  if (!env.TUGBOAT_APPLICATION_NAME || !env.TUGBOAT_REVISION){
    echo "TUGBOAT_APPLICATION_NAME and/or TUGBOAT_REVISION was null, likely because a deploy had not been started with Tugboat. This is not an error. Tugboat will not be notified of the build result."
    return
  }

  echo "Notifying Tugboat for ${env.TUGBOAT_APPLICATION_NAME} revision: ${env.TUGBOAT_REVISION} of this ${currentBuild.currentResult} pipeline."

  updateDeploymentCard(env.TUGBOAT_APPLICATION_NAME, "", env.TUGBOAT_REVISION)
}

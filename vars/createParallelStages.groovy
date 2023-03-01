// Method overloading to accept an Array
def call(Map options = [:], List<String> array, Closure block) {
  def params = array.collectEntries { element -> [element, null] }

  call(options, params, block)
}

// Groovy is smart enough to organize all named arguments into the options
// block. Any arguments that are actually map type will go afterwards.
def call(Map options = [:], Map params, Closure block) {
  script {
    def shouldFailFast = options.failFast ?: false
    def shallowClone = options.shallowClone ?: false
    def shallowCloneDepth = options.shallowCloneDepth ?: 1

    def stages = [:]
    params.each { param ->
      def stage_name = "${param.key}"
      stages[stage_name] = { ->
        stage(stage_name) {
          nodeWithWorkspace(options.label) {
            checkoutSCM(shallowClone, shallowCloneDepth).each { k,v -> env.setProperty(k, v) }

            if (param.value) {
              block(param.key, param.value)
            } else {
              block(param.key)
            }
          }
        }
      }
    }

    stages << [failFast: shouldFailFast]

    parallel(stages)
  }
}

// the checkout method uses hudson.util.DescribableList, which is a non-Serializable object
@NonCPS
def checkoutSCM(Boolean shallowClone, int shallowCloneDepth) {
  def extensions = scm.extensions
  if (shallowClone) {
    def cloneOptionExtension = [
      $class: "hudson.plugins.git.extensions.impl.CloneOption",
      shallow: true,
      depth: shallowCloneDepth,
      noTags: true,
    ]
    extensions += [cloneOptionExtension]
  }
  // This sets expected Git values as environment variables.
  // Stages created with declarative syntax do this by default.
  // https://issues.jenkins-ci.org/browse/JENKINS-45198
  def newScm = [
    $class: "GitSCM",
    branches: scm.branches,
    extensions: extensions,
    userRemoteConfigs: scm.userRemoteConfigs
  ]
  return checkout(newScm)
}

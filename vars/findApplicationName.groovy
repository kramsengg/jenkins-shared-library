def call(){
  def matcher = env.GIT_URL =~ /git@github.braintreeps.com:(braintree|cosmos)\/(?<applicationName>\S+)\.git/

  if (matcher) {
    return matcher.group("applicationName")
  } else {
    throw new Exception("Could not parse for the application name. Please specify it as an argument.")
  }
}

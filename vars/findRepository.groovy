def call() {
  def match = env.GIT_URL =~ /^git@github.braintreeps.com:(.+)\.git/
  if (match) {
    return match[0][1]
  } else {
    throw new Exception("could not infer `repository` from ${env.GIT_URL}")
  }
}

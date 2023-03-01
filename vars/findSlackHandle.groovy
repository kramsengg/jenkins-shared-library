def call(){
  def matcher = env.SLACK_HANDLE =~ /@(?<slackHandle>\S+)/

  if (matcher) {
    return matcher.group("slackHandle")
  } else {
    return "" 
  }
}

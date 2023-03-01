def call(Map options = [:]) {
  channel = options.get("channel", env.SLACK_CHANNEL)
  if (!channel) {
    message = "No Slack channel provided to notify"

    echo message
    throw new Exception(message)
  }

  return channel
}

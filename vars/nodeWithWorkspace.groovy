def call(String nodeLabel = null, Closure block) {
  def workspace = env.WORKSPACE
  node(nodeLabel) {
    List<String> meridianEndpoints = [
      "npm.paypal.com",
      "artifactory.paypalcorp.com",
      "engineering.paypalcorp.com",
      "artifactory.g.devqa.gcp.dev.paypalinc.com"
    ]

    // Do not use proxy for Meridian endpoints as they are local to the
    // VPC and not internet-routable. Saves time + shortens network path.
    env.no_proxy = env.no_proxy + "," + meridianEndpoints.join(",")
    if (workspace != null) {
      ws(workspace) {
        block()
      }
    }
    else {
      block()
    }
  }
}

package org.braintree

import com.timgroup.statsd.Event
import tools.braintree.DatadogClient

static String[] tags(Map options = [:], String jobName, String buildId) {
  def tags = [
  "deploy_application_id:${jobName}_${buildId}",
  "deploy_application:${jobName}",
  "deploy_build_id:${buildId}"
  ]

  if (options.target) {
    tags.add("deploy_target:${options.target}")
  }

  if (options.targets) {
    for (target in options.targets) {
      tags.add("deploy_target:${target}")
    }
  }

  if (options.revision) {
    tags.add("deploy_revision:${options.revision}")
  }

  return tags
}

static DatadogClient statsDClient(Map options = [:]) {
  return new DatadogClient("jenkinsfile_shared_libraries.pipeline", options.get("tags", []))
}

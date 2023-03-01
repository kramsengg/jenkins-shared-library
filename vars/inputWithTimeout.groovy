def call (Map options = [:]) {
  def DEFAULT_TIMEOUT_AMOUNT = 2
  def DEFAULT_TIMEOUT_UNIT = 'HOURS'

  script {
    def timeout_amount = env.cd_input_timeout ?: (options.timeout_amount ?: DEFAULT_TIMEOUT_AMOUNT)
    def timeout_unit = env.cd_input_timeout_unit ?: (options.timeout_unit ?: DEFAULT_TIMEOUT_UNIT)
    options.remove("timeout_amount")
    options.remove("timeout_unit")

    timeout(time: timeout_amount, unit: timeout_unit) {
      input(options)
    }
  }
}

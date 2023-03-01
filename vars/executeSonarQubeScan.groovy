import org.braintree.sonarqube.SonarQube

def call(Map options = [:]) {
    SonarQube sq = options.SonarQube ?: new SonarQube(this)
    options["sonarProjectPropertiesFilePath"] = options.get("sonarProjectPropertiesFilePath", "sonar-project.properties")
    echo "Starting SonarQube scan..."
    sq.validateProjectPropertiesFile(options["sonarProjectPropertiesFilePath"])
    sq.startScan()
}
def call() {
    return getBuildUser(currentBuild)
}

@NonCPS
def getBuildUser(currentBuild) {
    return currentBuild.rawBuild.getCauses().collect { it.getShortDescription() }.join('; ')
}

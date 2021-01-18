def call(Map args) {
    return isUnix() ? sh(args) : bat(args)
}

def call(String script) {
    return call(script: script)
}

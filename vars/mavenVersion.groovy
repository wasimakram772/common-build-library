/**
 *
 * @param args Map of arguments.
 * <ul><li>file (optional): Optional path to the file to read. If left empty the step will try to read pom.xml in the current working directory</li></ul>
 *
 * @return POM version OR POM parent version OR empty string
 */
def call(Map args = [:]) {
    def pom = args.file ? readMavenPom(file: args.file) : readMavenPom()
    return pom.version ?: pom.parent.version ?: ''
}

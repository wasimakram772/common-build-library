/**
 * This step is a convenience to run maven with different combinations of options and goals.
 *
 * @param args s a map of arguments.
 * <p/>
 * <strong>Usage:</strong>
 * <p/>
 * <pre>
 * buildWithMaven configId: 'some-id',<br/>
 *     options: ['-X', '-U'],<br/>
 *     clean: false,<br/>
 *     goals: ['package'],<br/>
 *     jvmParameters: [param: 'value'],<br/>
 *     sonar: [whenBranch:        'master',<br/>
 *             login:             'some login',<br/>
 *             password:          'some password',<br/>
 *             bitbucketPassword: 'some other password']
 * </pre>
 * <strong>Supported arguments:</strong>
 * <p/>
 * <strong>configId</strong>
 * <br/>
 * Should be a string. Will make maven use custom settings.xml configured in Jenkins by this id.
 * <p/>
 * <strong>options</strong>
 * <br/>
 * Should be a collection of strings. Will be joined with space and passed to maven as command line options.
 * <p/>
 * <strong>goals</strong>
 * <br/>
 * Should be a string OR a collection of strings OR a Map<String, Closure>.
 * <br/>
 * If it is a string or collection of strings, it will be passed to maven as goals.
 * <br/>
 * If it is a map, each key will be added to maven as goal if closure evaluates to <code>true</code>. When closure is invoked, single
 * Map<String, String> argument is passed to it. This map contains following keys:
 * <ul>
 *     <li>'version': maven project version read from pom.xml in current directory</li>
 *     <li>'branch<': git branch being currently built with jeckins</li>
 * </ul>
 * <p/>
 * <strong>jvmArguments</strong>
 * <br/>
 * Should be a Map<String, String>. Will be passed to maven as series of <code>-D&lt;key&gt;=&lt;argument&gt</code> parameters.
 * <p/>
 * <strong>sonar</strong>
 * <br/>
 * Should be a map with following structure:
 *
 * <code><pre>
 *     [login:             'Sonarqube login',
 *      password:          'Sonarqube password',
 *      bitbucketPassword: 'password that allows Sonarqube to notify Bitbucket server',
 *      whenBranch:        'optional, branch name'
 *     ]
 * </pre><code>
 * When login and password are specified, 'sonar:sonar' will be added to maven goals.
 * </br>
 * If 'whenBranch' is specified, 'sonar:sonar' will be added only if currently built branch has exactly the same name.
 * </br>
 * If 'bitbucketPassword' is specified and current build is a pull request build, this step will automatically configure sonar plugin for pull request analysis.
 * <p/>
 * When no <strong>goals</strong> are specified, this step will run '<code>mvn clean install</code>'.
 * <p/>
 * 'clean' will always be automatically added as a first goal; to disable this behavior pass '<code>clean: false</code>' to step arguments.
 * <p/>
 * <strong>Examples</strong>
 * <p/>
 * <code>buildWithMaven clean:false, goals: 'deploy'</code> will run <code>mvn deploy</code>
 * <p/>
 * <code>buildWithMaven goals: 'deploy'</code> will run <code>mvn clean deploy</code>
 * <p/>
 * <code>buildWithMaven goals: ['clean', 'deploy']</code> will run <code>mvn clean deploy</code>
 * <p/>
 * <code>buildWithMaven goals: [install: { arg -> arg.version.startsWith('-SNAPSHOT') }, deploy: {arg -> !arg.version.startsWith('-SNAPSHOT')}]</code> will run <code>mvn clean install</code> for snapshot versions or <code>mvn clean deploy</code> for non-snapshot versions.
 */
def call(Map args) {

    final List options = args.options instanceof List ? args.options as List : []
    final LinkedHashSet<String> goals = parseGoals(this, args)
    final Map jvmParameters = args.jvmParameters instanceof Map ? args.jvmParameters as Map : [:]

    String prefix = parseSonar(this, args, goals, jvmParameters)

    if (args.configId) {
        configFileProvider([configFile(fileId: args.configId, variable: 'buildWithMavenConfigFileVar')]) {
            options << ("-s $buildWithMavenConfigFileVar" as String)
            jvmParameters << ['archetype.test.settingsFile': "${buildWithMavenConfigFileVar}" as String]

            execute(this, prefix, options, goals, jvmParameters)
        }
    } else {
        execute(this, prefix, options, goals, jvmParameters)
    }
}

private static LinkedHashSet<String> parseGoals(def script, Map args) {
    final LinkedHashSet<String> goals = new LinkedHashSet<>()

    if (args.clean != false && args.clean != 'false') {
        goals << 'clean'
    }

    if (!args.goals) {
        goals << 'install'
    } else {

        def pom = script.readMavenPom()
        final String version = pom.version ?: pom.parent.version ?: ''

        if (args.goals instanceof Collection) {
            args.goals.collect { it as String }.collect { it.tokenize() }.flatten().grep().each { goals << it }

        } else if (args.goals instanceof Map) {
            Map goalsMap = args.goals as Map

            Map conditionArguments = ['branch': getBranch(script), 'version': version]

            goalsMap.each { goal, condition ->
                if (condition instanceof Closure) {
                    if (condition.call(conditionArguments)) {
                        goals << (goal as String)
                    }
                }
            }

        } else if (args.goals) {
            (args.goals as String).tokenize().grep().each { goals << it }
            goals << (args.goals as String)
        }
    }
    return goals
}

private static String parseSonar(def script, Map args, LinkedHashSet<String> goals, Map jvmParameters) {

    String prefix = ''

    final Map sonar = args.sonar instanceof Map ? args.sonar as Map : [:]

    if (sonar.login && sonar.password && sonarEnabled(script, sonar)) {

        if (script.isUnix()) {
            prefix = "SONAR_USER_HOME=${script.WORKSPACE}/.sonar"
        } else {
            prefix = "set \"SONAR_USER_HOME=${script.WORKSPACE}\\.sonar\" &&"
        }

        goals << 'sonar:sonar'

        jvmParameters << ['sonar.login': sonar.login]
        jvmParameters << ['sonar.password': sonar.password]
        jvmParameters << ['jacoco.skip': 'false']

        if (sonar.propertiesFromPom) {
            def pom = script.readMavenPom(file: sonar.propertiesFromPom)
            Map<String, String> pomProperties = new HashMap<>(pom.properties)

            pomProperties.each { name, value ->
                if (name.startsWith('sonar.')) {
                    jvmParameters.put(name, value)
                }
            }

            if (pom.scm?.url) {
                jvmParameters << ['sonar.links.scm': pom.scm.url]
            }
        }

        Map prData = script.pullRequestData()

        if (sonar.developersEdition && (script.env.BRANCH_NAME ?: 'master') != 'master' && !prData) {
            jvmParameters << ['sonar.branch.name': script.env.BRANCH_NAME]
        }

        if (sonar.bitbucketPassword && prData) {

            jvmParameters << ['sonar.stash.password': sonar.bitbucketPassword]
            jvmParameters << ['sonar.stash.project': prData.project]
            jvmParameters << ['sonar.stash.repository': prData.repository]

            if (sonar.developersEdition) {
                jvmParameters << ['sonar.pullrequest.key': prData.id]
                jvmParameters << ['sonar.pullrequest.branch': prData.sourceBranch]
                jvmParameters << ['sonar.pullrequest.base': prData.targetBranch]

            } else {
                jvmParameters << ['sonar.stash.notification': 'false']
                jvmParameters << ['sonar.stash.comments.reset': '']
                jvmParameters << ['sonar.stash.pullrequest.id': prData.id]
                jvmParameters << ['sonar.branch': prData.sourceBranch]
            }
        }
    }

    return prefix
}

private static boolean sonarEnabled(def script, Map sonar) {
    if (sonar.whenBranch) {
        final Set<String> allowedBranches = new HashSet<>()

        if (sonar.whenBranch instanceof CharSequence) {
            allowedBranches << (sonar.whenBranch as String)
        } else if (sonar.whenBranch instanceof Collection) {
            sonar.whenBranch.collect { it as String }.each { allowedBranches << it }
        }

        def branch = getBranch(script)

        if (branch) {
            return allowedBranches.contains(branch)
        } else {
            script.echo('buildWithMaven: cannot determine current git branch; sonar plugin will not run')
            return false
        }

    } else {
        return true
    }
}

private static void execute(def script, String prefix, List<String> options, Collection<String> goals, Map jvmParameters) {
    def commandLineParts = [
            prefix,
            "mvn",
            options,
            goals,
            jvmParameters.collect { key, value -> "-D$key" + (value ? "=$value" : '') }
    ]

    def commandLine = commandLineParts.flatten().grep().join(' ')

    if (jvmParameters.containsKey('sonar.stash.pullrequest.id')) {
        script.createPullRequestSummary()
    }

    if (script.isUnix()) {
        script.sh commandLine
    } else {
        script.bat commandLine
    }

    if (goals.contains('sonar:sonar')) {
        script.createSonarSummary()
    }
}

private static String getBranch(def script) {
    if (script.env.BRANCH_NAME) {
        return script.env.BRANCH_NAME
    } else if (script.env.GIT_BRANCH) {
        return script.env.GIT_BRANCH
    } else {
        return ''
    }
}

def call() {

    def result = [:]

    if (env.CHANGE_URL) {
        result = [
                'id'          : env.CHANGE_ID,
                'project'     : extract(env.CHANGE_URL, /projects\/(.+?)\//),
                'repository'  : extract(env.CHANGE_URL, /repos\/(.+?)\//),
                'sourceBranch': env.CHANGE_BRANCH,
                'targetBranch': env.CHANGE_TARGET,
                'author'      : env.CHANGE_AUTHOR,
                'authorEmail' : env.CHANGE_AUTHOR_EMAIL,
                'url'         : env.CHANGE_URL
        ]

        result << ['message': message(result, this)]

    } else if (params.PR_PROJECT) {
        result = [
                'id'          : params.PR_ID,
                'project'     : params.PR_PROJECT,
                'repository'  : params.PR_REPO,
                'sourceBranch': params.SOURCE_BRANCH,
                'targetBranch': params.TARGET_BRANCH,
                'author'      : params.PR_AUTHOR,
                'url'         : extract(env.GET_URL, /(.+?)\\/scm/) + "/projects/${params.PR_PROJECT}/repos/${params.PR_REPO}/pull-requests/${params.PR_ID}/overview"
        ]

        result << ['message': message(result, this)]
    }

    return result
}

def extract(String s, String pattern) {
    def matcher = s =~ pattern

    matcher.find() ? matcher.group(1) : ''
}

String message(Map data, script) {
    def email = data.authorEmail ? "@${data.authorEmail}" : script.relevantCommitters()
    return "<${data.url}|Pull request> <${script.env.BUILD_URL}|build> from *${data.repository}/${data.sourceBranch}* to *${data.targetBranch}* by ${email}"
}

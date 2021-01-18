def call(Map args = [:]) {

    if (args.message) {
        slackSend(
                color: args.color ? colors.byName(args.color) : null,
                channel: args.channel ?: 'ci_wfstudio_i_e',
                teamDomain: 'work-fusion',
                token: args.token,
                message: args.message
        )

    } else if (args.buildMessage) {

        String msg = ''

        Map prData = pullRequestData()
        if (prData) {
            def email = prData.authorEmail ? "@${prData.authorEmail}" : relevantCommitters()

            msg = "Pull request build from *${prData.repository}/${prData.sourceBranch}* to *${prData.targetBranch}* by ${email} *${args.buildMessage}*"
            msg += "\n*Pull request link:* <${prData.url}|here>"

        } else {
            msg = env.BRANCH_NAME ? "*${currentBuild.rawBuild.project.parent.displayName}/${env.BRANCH_NAME}*" : "*${env.JOB_NAME}*"
            msg += " build *${args.buildMessage}*"
        }

        msg += "\n*Jenkins build link:* <${env.BUILD_URL}|here>"
        if (args.sonarLink) {
            def url = sonarUrl(host: args.sonarHost, developersEdition: args.sonarDevelopersEdition)
            msg += "\n*SonarQube link:* <${url}|here>"
        }

        slackSend(
                color: args.color ? colors.byName(args.color) : null,
                channel: args.channel ?: 'ci_wfstudio_i_e',
                teamDomain: 'work-fusion',
                token: args.token,
                message: msg
        )
    }
}

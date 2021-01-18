def call(Map args = [condition: true, message: 'failWhen step is called without arguments']) {
    boolean errorCondition = false

    switch (args.condition) {
        case Closure:
            errorCondition = (args.condition as Closure).call()
            break
        case Boolean:
            errorCondition = args.condition as Boolean
            break
    }

    if (errorCondition) {
        def message = args.message ?: 'Error condition is true'

        addErrorBadge text: message
        error message
    }
}

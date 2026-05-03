

export function getBpmnType(type: string): string {
    switch (type) {
        case 'SpringBean':
            return 'bpmn:ServiceTask';
        case 'SpringExternalTask':
            return 'bpmn:ServiceTask';
        case 'CallActivity':
            return 'bpmn:CallActivity';
        default:
            return 'bpmn:ServiceTask';
    }
}


export function isServiceTask(type: string): boolean {
    return type === 'SpringBean' || type === 'SpringExternalTask';
}

export function isExternalTask(type: string): boolean {
    return type === 'SpringExternalTask';
}

export function isCallActivity(type: string): boolean {
    return type === 'CallActivity';
}

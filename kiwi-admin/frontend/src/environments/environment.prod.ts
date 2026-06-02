const ip = 'api.kiwi-admin.cn';
const port = '80';
export const localUrl = `http://${ip}:${port}`;


export const environment = {
  production: false,
  appName: 'Kiwi Admin',
  api: {
    baseUrl: localUrl,
    /** Camunda spring-boot-starter-rest 默认 `/engine-rest`，若改 context 请同步 */
    camundaEngineRestPath: '/engine-rest',
  },
};

const ip = '10.15.56.173';
const port = '8000';
export const localUrl = `http://${ip}:${port}/kiwi-be`;


export const environment = {
  production: false,
  appName: 'Kiwi Admin',
  api: {
    baseUrl: localUrl,
    /** Camunda spring-boot-starter-rest 默认 `/engine-rest`，若改 context 请同步 */
    camundaEngineRestPath: '/engine-rest',
  },
};

const ip = 'api.kiwi-admin.cn';
const port = '80';
export const localUrl = `http://${ip}:${port}`;


export const environment = {
  production: true,
  appName: 'Kiwi Admin',
  /** 在线演示：登录后直达 BPM 项目管理，便于快速体验流程编排 */
  postLoginPath: '/bpm/project',
  api: {
    baseUrl: localUrl,
    /** Operaton/Camunda REST 默认 `/engine-rest`（路径名未变），若改 context 请同步 */
    camundaEngineRestPath: '/engine-rest',
  },
};

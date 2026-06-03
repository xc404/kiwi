/** Docker Compose：经 Nginx 反代后端，API 使用同源相对路径 */
export const environment = {
  production: true,
  appName: 'Kiwi Admin',
  postLoginPath: '/bpm/project',
  api: {
    baseUrl: '',
    camundaEngineRestPath: '/engine-rest',
  },
};

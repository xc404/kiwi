// This file can be replaced during build by using the `fileReplacements` array.
// `ng build` replaces `environment.ts` with `environment.prod.ts`.
// The list of file replacements can be found in `angular.json`.
const ip = 'localhost';
const port = '8088';
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

/*
 * For easier debugging in development mode, you can import the following file
 * to ignore zone related error stack frames such as `zone.run`, `zoneDelegate.invokeTask`.
 *
 * This import should be commented out in production mode because it will have a negative impact
 * on performance if an error is thrown.
 */
// import 'zone.js/plugins/zone-error';  // Included with Angular CLI.

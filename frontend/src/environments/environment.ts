export const environment = {
  production: false,
  apiBaseUrl: 'http://172.21.172.136:8080',
  // When true, frontend will rely on the dev server proxy (ng serve --proxy-config)
  // and will not prefix /api calls with an absolute base. Set to false to call
  // the backend directly via environment.apiBaseUrl.
  // For local development the dev server can proxy /api to the backend
  // so the browser sees same-origin requests and cookies are handled
  // without cross-site cookie rules. Use the proxy when developing on LAN
  // (run with `ng serve --proxy-config proxy.conf.json --host 0.0.0.0`).
  useProxy: true
};

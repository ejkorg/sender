export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
  // When true, frontend will rely on the dev server proxy (ng serve --proxy-config)
  // and will not prefix /api calls with an absolute base. Set to false to call
  // the backend directly via environment.apiBaseUrl.
  useProxy: true
};

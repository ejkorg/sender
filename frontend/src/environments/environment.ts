export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
  // When true, frontend will rely on the dev server proxy (ng serve --proxy-config)
  // and will not prefix /api calls with an absolute base. Set to false to call
  // the backend directly via environment.apiBaseUrl.
  // For sharing your dev instance on the LAN, disable the proxy so the
  // frontend will call the backend directly (and will resolve the host at
  // runtime to the machine's IP). Set to false for easier network access.
  useProxy: false
};

// Dev proxy: same origin as the Angular dev server, so the backend needs no CORS config.
const toBackend = {
  target: 'http://localhost:8080',
  on: {
    // Without a Basic challenge the browser doesn't pop its native login dialog on 401; the app has its own form.
    proxyRes: (res) => delete res.headers['www-authenticate'],
  },
};

export default { '/admin': toBackend, '/actuator': toBackend };

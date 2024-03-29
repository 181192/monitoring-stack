const express = require('express');
const prometheus = require('prom-client');
const app = express();
const port = process.env.PORT || 8080;

const httpRequestsTotal = new prometheus.Counter({
  name: 'http_server_requests_total',
  help: 'Number of HTTP operations',
  labelNames: ['method', 'status', 'uri']
});

const httpRequestDuration = new prometheus.Histogram({
  name: 'http_server_request_seconds',
  help: 'Duration of HTTP requests in seconds',
  labelNames: ['method', 'status', 'uri'],
  buckets: prometheus.linearBuckets(0.005, 10, 11)
});

const openMetricsMIME = 'application/openmetrics-text';

app.get('/metrics', async (req, res) => {

  if (req.headers['accept'] === openMetricsMIME) {
    prometheus.register.setContentType(prometheus.openMetricsContentType);
  } else {
    prometheus.register.setContentType(prometheus.prometheusContentType);
  }

  res.setHeader('content-type', prometheus.register.contentType);
  const metrics = await prometheus.register.metrics();
  res.send(metrics);
});

app.get('/healthz', (req, res) => {
  res.json({ status: 'UP' });
});

app.get('/readyz', (req, res) => {
  res.json({ status: 'UP' });
});

app.get('/ping', (req, res) => {
  const start = Date.now();
  const sleepDuration = Math.random() * 1000;
  console.log(`Sleeping for ${sleepDuration / 1000} seconds`);

  setTimeout(() => {
    res.send('pong');
    const end = Date.now();
    const duration = end - start;

    httpRequestDuration
      .labels(req.method, res.statusCode, req.path)
      .observe(duration / 1000);
    httpRequestsTotal
      .labels(req.method, res.statusCode, req.path)
      .inc();
  }
    , sleepDuration);
});

const server = app.listen(port, () => {
  console.log(`Starting server at port ${port}!`);
});

process.on('SIGTERM', () => {
  server.close((err) => {
    if (err) {
      console.error(err);
      process.exit(1);
    }

    process.exit(0);
  })
});

const winston = require('winston');
const express = require("express");
const prometheus = require("prom-client");

const logger = winston.createLogger({
  transports: [new winston.transports.Console()],
});

const app = express();
const port = process.env.PORT || 8080;
const googleMapsApiKey = process.env.GOOGLE_MAPS_API_KEY;


const httpRequestsTotal = new prometheus.Counter({
  name: "http_server_requests_total",
  help: "Number of HTTP operations",
  labelNames: ["method", "status", "path"],
});

const httpRequestDuration = new prometheus.Histogram({
  name: "http_server_request_seconds",
  help: "Duration of HTTP requests in seconds",
  labelNames: ["method", "status", "path"],
  buckets: prometheus.linearBuckets(0.005, 10, 11),
});

const openMetricsMIME = "application/openmetrics-text";

app.get("/metrics", async (req, res) => {
  if (req.headers["accept"] === openMetricsMIME) {
    prometheus.register.setContentType(prometheus.openMetricsContentType);
  } else {
    prometheus.register.setContentType(prometheus.prometheusContentType);
  }

  res.setHeader("content-type", prometheus.register.contentType);
  const metrics = await prometheus.register.metrics();
  res.send(metrics);
});

app.get("/healthz", (_, res) => {
  res.json({ status: "UP" });
});

app.get("/readyz", (_, res) => {
  res.json({ status: "UP" });
});

app.get("/reverse-geocode", async (req, res) => {
  const start = Date.now();

  const latitude = req.query.latitude;
  const longitude = req.query.longitude;

  if (!latitude || !longitude) {
    res.status(400).json({ error: 'Missing latitude or longitude' });
    return;
  }

  logger.info(`Get reverse geocoding ${latitude},${longitude}`);

  const url = `https://maps.googleapis.com/maps/api/geocode/json?latlng=${latitude},${longitude}&language=no&key=${googleMapsApiKey}`
  const response = await fetch(url);
  if (!response.ok) {
    logger.warn(`Error fetching geocode status=${response.status} response=${await response.text()}`);
    res.status(500).json({ error: 'Something broke!' });
    return;
  }

  const data = await response.json();

  const address = data.results?.[0]?.formatted_address;
  logger.info(`Found reverse geocoding ${latitude},${longitude} -> ${address}`);

  res.json({
    address: address
  });

  const end = Date.now();
  const duration = end - start;

  httpRequestDuration
    .labels(req.method, res.statusCode, req.path)
    .observe(duration / 1000);
  httpRequestsTotal.labels(req.method, res.statusCode, req.path).inc();
});

app.get("/weather", async (req, res) => {
  const start = Date.now();

  const latitude = req.query.latitude;
  const longitude = req.query.longitude;
  const url = `https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${latitude}&lon=${longitude}`

  const response = await fetch(url);
  if (!response.ok) {
    logger.warn(`Error fetching weather data status=${response.status} response=${await response.text()}`);
    res.status(500).send('Something broke!');
    return;
  }

  const data = await response.json();

  logger.info(`Found weather data ${latitude},${longitude} -> updated at ${data.properties.meta.updated_at}`);

  res.json({
    temperature: data.properties.timeseries[0].data.instant.details.air_temperature,
    windSpeed: data.properties.timeseries[0].data.instant.details.wind_speed,
    weatherSymbol: data.properties.timeseries[0].data.next_1_hours.summary.symbol_code
  });

  const end = Date.now();
  const duration = end - start;

  httpRequestDuration
    .labels(req.method, res.statusCode, req.path)
    .observe(duration / 1000);
  httpRequestsTotal.labels(req.method, res.statusCode, req.path).inc();
});

const server = app.listen(port, () => {
  logger.info(`Starting server at port ${port}!`);
});

process.on("SIGTERM", async () => {
  logger.info("SIGTERM signal received: closing HTTP server");

  server.close((err) => {
    if (err) {
      logger.error(err);
      process.exit(1);
    }

    process.exit(0);
  });
});

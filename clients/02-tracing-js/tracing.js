'use strict'

const process = require('process');
const opentelemetry = require('@opentelemetry/sdk-node');
const { getNodeAutoInstrumentations } = require('@opentelemetry/auto-instrumentations-node');
const { ConsoleSpanExporter } = require('@opentelemetry/sdk-trace-base');
const { Resource } = require('@opentelemetry/resources');
const { SemanticResourceAttributes } = require('@opentelemetry/semantic-conventions');
const { HttpInstrumentation } = require('@opentelemetry/instrumentation-http')
const { ExpressInstrumentation } = require('@opentelemetry/instrumentation-express')
const { WinstonInstrumentation } = require('@opentelemetry/instrumentation-winston');

const serviceName = process.env.SERVICE_NAME || '02-tracing-js';
const exportMode = process.env.OTEL_EXPORTER_EXPORT_MODE;
const collectorUrl = process.env.OTEL_EXPORTER_OTLP_HTTP_ENDPOINT || 'http://localhost:4318/v1/traces';

let traceExporter;
if (exportMode === 'stdout') {
  traceExporter = new ConsoleSpanExporter();
} else {
  const { OTLPTraceExporter } = require('@opentelemetry/exporter-trace-otlp-http');
  traceExporter = new OTLPTraceExporter({
    url: collectorUrl,
  });
}

// configure the SDK to export telemetry data to the console
// enable all auto-instrumentations from the meta package
const sdk = new opentelemetry.NodeSDK({
  traceExporter,
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: serviceName,
  }),
  instrumentations: [
    // getNodeAutoInstrumentations(),
    new HttpInstrumentation({
      ignoreIncomingRequestHook: (req) => {
        return req.url === '/metrics' || req.url === '/healthz' || req.url === '/readyz';
      }
    }),
    // new ExpressInstrumentation(),
    new WinstonInstrumentation(),
  ]
});

// initialize the SDK and register with the OpenTelemetry API
// this enables the API to record telemetry
sdk.start();

// gracefully shut down the SDK on process exit
process.on('SIGTERM', () => {
  sdk.shutdown()
    .then(() => console.log('Tracing terminated'))
    .catch((error) => console.log('Error terminating tracing', error))
    .finally(() => process.exit(0));
});

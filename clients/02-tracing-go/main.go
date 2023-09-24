package main

import (
	"context"
	"encoding/json"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/gin-contrib/logger"
	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/exporters/stdout/stdouttrace"
	"go.opentelemetry.io/otel/propagation"

	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
)

var (
	port              = getEnv("PORT", "8080")
	serviceName       = getEnv("SERVICE_NAME", "02-tracing-go")
	collectorURL      = getEnv("OTEL_EXPORTER_OTLP_GRPC_ENDPOINT", "localhost:4317")
	exportMode        = getEnv("OTEL_EXPORTER_OTLP_EXPORT_MODE", "")
	weatherServiceURL = getEnv("WEATHER_SERVICE_URL", "http://localhost:8080")
	environment       = getEnv("ENVIRONMENT", "development")
)

func getEnv(key, fallback string) string {
	value, exists := os.LookupEnv(key)
	if !exists {
		value = fallback
	}
	return value
}

var (
	httpRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "http_server_requests_total",
			Help: "Number of HTTP operations",
		},
		[]string{"method", "status", "uri"},
	)

	httpRequestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "http_server_request_seconds",
			Help:    "Duration of HTTP requests in seconds",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"method", "status", "uri"},
	)
)

func getTraceExporter() (sdktrace.SpanExporter, error) {
	if exportMode == "stdout" {
		return stdouttrace.New(
			// Use human-readable output.
			stdouttrace.WithPrettyPrint(),
			// Do not print timestamps for the demo.
			stdouttrace.WithoutTimestamps(),
		)
	}

	if len(collectorURL) == 0 {
		log.Fatal().Msg("OTEL_EXPORTER_OTLP_GRPC_ENDPOINT environment variable not set")
	}

	return otlptrace.New(
		context.Background(),
		otlptracegrpc.NewClient(
			otlptracegrpc.WithInsecure(),
			otlptracegrpc.WithEndpoint(collectorURL),
		),
	)
}

func initTracer() func(context.Context) error {
	exporter, err := getTraceExporter()
	if err != nil {
		log.Fatal().Err(err).Msg("Failed to create the collector trace exporter")
	}

	resources, err := resource.New(
		context.Background(),
		resource.WithAttributes(
			attribute.String("service.name", serviceName),
			attribute.String("library.language", "go"),
		),
	)
	if err != nil {
		log.Warn().Err(err).Msg("Could not set resources")
	}

	otel.SetTracerProvider(
		sdktrace.NewTracerProvider(
			sdktrace.WithSampler(sdktrace.AlwaysSample()),
			sdktrace.WithBatcher(exporter),
			sdktrace.WithResource(resources),
		),
	)

	otel.SetTextMapPropagator(
		propagation.NewCompositeTextMapPropagator(
			propagation.TraceContext{},
			propagation.Baggage{},
		),
	)

	return exporter.Shutdown
}

func healthzHandler(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"message": "ok"})
}

func readyzHandler(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"message": "ok"})
}

type Weather struct {
	Message       string  `json:"message"`
	Address       string  `json:"address"`
	Temperature   float64 `json:"temperature"`
	WindSpeed     float64 `json:"windSpeed"`
	WeatherSymbol string  `json:"weatherSymbol"`
}

func weatherHandler(c *gin.Context) {
	start := time.Now()

	longitude := c.Query("longitude")
	latitude := c.Query("latitude")

	if len(longitude) == 0 || len(latitude) == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"message": "Missing longitude or latitude"})
		return
	}

	context := c.Request.Context()
	span := trace.SpanFromContext(context)
	defer span.End()

	uri := c.Request.URL.Path
	method := c.Request.Method

	exemplarsLabels := prometheus.Labels{
		"trace_id": span.SpanContext().TraceID().String(),
		"span_id":  span.SpanContext().SpanID().String(),
	}

	otel.GetTextMapPropagator().Inject(context, propagation.HeaderCarrier(c.Request.Header))
	url := weatherServiceURL + "/weather?longitude=" + longitude + "&latitude=" + latitude
	res, err := httpGet(context, url)
	if err != nil {
		log.Warn().Err(err).Msg("Error fetching weather")
		c.JSON(http.StatusInternalServerError, gin.H{"message": "Error fetching weather"})

		duration := time.Since(start)
		status := strconv.Itoa(http.StatusInternalServerError)
		httpRequestDuration.WithLabelValues(method, status, uri).(prometheus.ExemplarObserver).
			ObserveWithExemplar(duration.Seconds(), exemplarsLabels)
		httpRequestsTotal.WithLabelValues(method, status, uri).(prometheus.ExemplarAdder).
			AddWithExemplar(1, exemplarsLabels)
		return
	}

	var weather Weather
	err = json.NewDecoder(res.Body).Decode(&weather)
	if err != nil {
		log.Warn().Err(err).Msg("Error decoding response body")
		c.JSON(http.StatusInternalServerError, gin.H{"message": "Error decoding response body"})
		return
	}

	if c.Request.Header.Get("Content-Type") == "application/json" {
		c.JSON(http.StatusOK, weather)
	} else {
		c.HTML(http.StatusOK, "weather.tmpl", gin.H{
			"message":       weather.Message,
			"address":       weather.Address,
			"temperature":   weather.Temperature,
			"windSpeed":     weather.WindSpeed,
			"weatherSymbol": weather.WeatherSymbol,
		})
	}

	duration := time.Since(start)
	status := strconv.Itoa(http.StatusOK)
	httpRequestDuration.WithLabelValues(method, status, uri).(prometheus.ExemplarObserver).
		ObserveWithExemplar(duration.Seconds(), exemplarsLabels)
	httpRequestsTotal.WithLabelValues(method, status, uri).(prometheus.ExemplarAdder).
		AddWithExemplar(1, exemplarsLabels)
}

func httpGet(ctx context.Context, url string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, http.NoBody)
	if err != nil {
		return nil, err
	}

	httpClient := &http.Client{
		Timeout: 10 * time.Second,
		Transport: otelhttp.NewTransport(http.DefaultTransport,
			otelhttp.WithPropagators(otel.GetTextMapPropagator()),
			otelhttp.WithSpanOptions(trace.WithAttributes(
				attribute.String("component", "opentracing-example"),
			)),
		),
	}

	res, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}

	return res, nil
}

var loggerHandler = logger.SetLogger(
	logger.WithLogger(func(c *gin.Context, l zerolog.Logger) zerolog.Logger {
		l = l.Output(gin.DefaultWriter).With().Logger()

		if trace.SpanFromContext(c.Request.Context()).SpanContext().IsValid() {
			l = l.With().
				Str("trace_id", trace.SpanFromContext(c.Request.Context()).SpanContext().TraceID().String()).
				Str("span_id", trace.SpanFromContext(c.Request.Context()).SpanContext().SpanID().String()).
				Logger()
		}

		return l.With().
			Str("path", c.Request.URL.Path).
			Logger()
	}),
)

func main() {

	cleanup := initTracer()
	defer cleanup(context.Background())

	registry := prometheus.NewRegistry()
	registry.MustRegister(httpRequestsTotal)
	registry.MustRegister(httpRequestDuration)

	if environment == "production" {
		gin.SetMode(gin.ReleaseMode)
	}

	r := gin.New()
	r.Use(gin.Recovery())
	r.Use(otelgin.Middleware(serviceName, otelgin.WithFilter(func(r *http.Request) bool {
		return r.URL.Path != "/metrics" && r.URL.Path != "/healthz" && r.URL.Path != "/readyz"
	})))
	r.LoadHTMLGlob("templates/*")

	r.GET("/metrics", gin.WrapH(promhttp.HandlerFor(registry, promhttp.HandlerOpts{
		EnableOpenMetrics: true,
	})))

	r.GET("/healthz", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	r.GET("/readyz", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	r.GET("/weather", loggerHandler, weatherHandler)

	log.Printf("Starting server at port %s", port)
	err := r.Run(":" + port)
	if err != nil {
		log.Fatal().Err(err).Msg("Error starting server")
	}
}

package main

import (
	"context"
	"log"
	"math/rand"
	"net/http"
	"os"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/exporters/stdout/stdouttrace"
	"go.opentelemetry.io/otel/propagation"

	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
)

var (
	serviceName  = os.Getenv("SERVICE_NAME")
	collectorURL = os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
	exportMode   = os.Getenv("OTEL_EXPORTER_EXPORT_MODE")
)

var (
	httpRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "http_requests_total",
			Help: "Number of HTTP operations",
		},
		[]string{"method", "status", "path"},
	)

	httpRequestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "http_request_duration_seconds",
			Help:    "Duration of HTTP requests in seconds",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"method", "status", "path"},
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
		log.Fatalf("OTEL_EXPORTER_OTLP_ENDPOINT environment variable not set")
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
		log.Fatal(err)
	}

	resources, err := resource.New(
		context.Background(),
		resource.WithAttributes(
			attribute.String("service.name", serviceName),
			attribute.String("library.language", "go"),
		),
	)
	if err != nil {
		log.Println("Could not set resources: ", err)
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

func pingHandler(c *gin.Context) {
	start := time.Now()
	sleepDuration := time.Duration(rand.Intn(3))
	log.Printf("Sleeping for %v seconds", sleepDuration)
	time.Sleep(sleepDuration * time.Second) // Sleep for a random duration between 0 and 1 seconds

	duration := time.Since(start)
	httpRequestDuration.WithLabelValues("GET", "200", "/ping").Observe(duration.Seconds())
	httpRequestsTotal.WithLabelValues("GET", "200", "/ping").Inc()

	c.JSON(http.StatusOK, gin.H{"message": "pong"})
}

func healthzHandler(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"message": "ok"})
}

func readyzHandler(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"message": "ok"})
}

func main() {
	cleanup := initTracer()
	defer cleanup(context.Background())

	registry := prometheus.NewRegistry()
	registry.MustRegister(httpRequestsTotal)
	registry.MustRegister(httpRequestDuration)

	r := gin.Default()
	r.Use(otelgin.Middleware(serviceName))
	r.GET("/metrics", gin.WrapH(promhttp.HandlerFor(registry, promhttp.HandlerOpts{
		EnableOpenMetrics: true,
	})))

	r.GET("/healthz", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	r.GET("/readyz", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	r.GET("/ping", pingHandler)

	log.Println("Starting server at port 8080")
	err := r.Run(":8080")
	if err != nil {
		log.Fatalf("Error starting server: %v", err)
	}
}

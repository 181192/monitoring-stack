package main

import (
	"fmt"
	"log"
	"math/rand"
	"net/http"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

var httpRequestsTotal = prometheus.NewCounterVec(
	prometheus.CounterOpts{
		Name: "http_requests_total",
		Help: "Number of HTTP operations",
	},
	[]string{"method", "status", "path"},
)

var httpRequestDuration = prometheus.NewHistogramVec(
	prometheus.HistogramOpts{
		Name:    "http_request_duration_seconds",
		Help:    "Duration of HTTP requests in seconds",
		Buckets: prometheus.DefBuckets,
	},
	[]string{"method", "status", "path"},
)

func pingHandler(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	sleepDuration := time.Duration(rand.Intn(3))
	log.Printf("Sleeping for %v seconds", sleepDuration)
	time.Sleep(sleepDuration * time.Second) // Sleep for a random duration between 0 and 1 seconds

	w.WriteHeader(http.StatusOK)
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprintf(w, "{\"message\": \"pong\"}")

	duration := time.Since(start)
	httpRequestDuration.WithLabelValues("GET", "200", "/ping").Observe(duration.Seconds())
	httpRequestsTotal.WithLabelValues("GET", "200", "/ping").Inc()
}

func healthzHandler(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprintf(w, "{\"status\": \"UP\"}")
}

func readyzHandler(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprintf(w, "{\"status\": \"UP\"}")
}

func main() {
	registry := prometheus.NewRegistry()
	registry.MustRegister(httpRequestsTotal)
	registry.MustRegister(httpRequestDuration)

	http.Handle("/metrics", promhttp.HandlerFor(registry, promhttp.HandlerOpts{}))
	http.HandleFunc("/healthz", healthzHandler)
	http.HandleFunc("/readyz", readyzHandler)
	http.HandleFunc("/ping", pingHandler)

	log.Println("Starting server at port 8080")
	err := http.ListenAndServe(":8080", nil)
	if err != nil {
		log.Fatalf("Error starting server: %v", err)
	}
}

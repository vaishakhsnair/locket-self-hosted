package main

import (
	"log"
	"net/http"
	"os"
	"path/filepath"

	"locket/server/internal/store"
)

func main() {
	addr := getenv("LOCKET_ADDR", ":8080")
	dataDir := getenv("LOCKET_DATA_DIR", filepath.Join(".", "data"))

	repo, err := store.New(dataDir)
	if err != nil {
		log.Fatal(err)
	}
	server := store.NewServer(repo, getenv("LOCKET_ACCESS_TOKEN", ""))
	log.Printf("locket server listening on %s; data=%s", addr, dataDir)
	if err := http.ListenAndServe(addr, server); err != nil {
		log.Fatal(err)
	}
}

func getenv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

package store

import (
	"crypto/subtle"
	"encoding/json"
	"html"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

func NewServer(repo *Repository, accessToken string) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.HandleFunc("GET /connect", func(w http.ResponseWriter, r *http.Request) {
		// Browsers commonly search pasted custom schemes instead of launching them.
		// This public, no-store handoff page gives the user a real HTTPS/HTTP URL
		// to tap, then launches the app's custom scheme from that user gesture.
		appLink := (&url.URL{Scheme: "locket", Host: "connect", RawQuery: r.URL.RawQuery}).String()
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		_, _ = io.WriteString(w, `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Open Locket</title><style>body{font-family:system-ui,sans-serif;max-width:30rem;margin:18vh auto;padding:24px;background:#fff8f6;color:#241a18}a{display:inline-block;padding:14px 20px;border-radius:999px;background:#b3261e;color:white;text-decoration:none;font-weight:700}</style></head><body><h1>Open Locket</h1><p>This private setup link is ready to import.</p><a href="`+html.EscapeString(appLink)+`">Open in Locket</a></body></html>`)
	})
	mux.HandleFunc("GET /v1/events", func(w http.ResponseWriter, r *http.Request) {
		flusher, ok := w.(http.Flusher)
		if !ok {
			http.Error(w, "streaming unsupported", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Connection", "keep-alive")
		w.Header().Set("X-Accel-Buffering", "no")
		updates, unsubscribe := repo.Subscribe()
		defer unsubscribe()
		_, _ = io.WriteString(w, ": connected\n\n")
		flusher.Flush()
		heartbeat := time.NewTicker(20 * time.Second)
		defer heartbeat.Stop()
		for {
			select {
			case <-r.Context().Done():
				return
			case photo := <-updates:
				encoded, err := json.Marshal(photo)
				if err != nil {
					continue
				}
				_, _ = io.WriteString(w, "event: photo\ndata: "+string(encoded)+"\n\n")
				flusher.Flush()
			case <-heartbeat.C:
				_, _ = io.WriteString(w, ": heartbeat\n\n")
				flusher.Flush()
			}
		}
	})
	mux.HandleFunc("GET /v1/photos", func(w http.ResponseWriter, r *http.Request) {
		photos := repo.List(r.URL.Query().Get("before"), r.URL.Query().Get("partner"), 40)
		var next string
		if len(photos) == 40 {
			next = strconv.FormatInt(photos[len(photos)-1].CapturedAt.UnixNano(), 10)
		}
		writeJSON(w, http.StatusOK, map[string]any{"photos": photos, "nextCursor": next})
	})
	mux.HandleFunc("GET /v1/photos/", func(w http.ResponseWriter, r *http.Request) {
		id := strings.TrimPrefix(r.URL.Path, "/v1/photos/")
		if strings.HasSuffix(id, "/ciphertext") {
			id = strings.TrimSuffix(id, "/ciphertext")
			media, err := repo.Media(id)
			if err != nil {
				http.Error(w, "not found", http.StatusNotFound)
				return
			}
			w.Header().Set("Content-Type", "application/octet-stream")
			_, _ = w.Write(media)
			return
		}
		http.NotFound(w, r)
	})
	mux.HandleFunc("POST /v1/photos", func(w http.ResponseWriter, r *http.Request) {
		defer r.Body.Close()
		body, err := io.ReadAll(io.LimitReader(r.Body, 12<<20))
		if err != nil {
			http.Error(w, "invalid body", http.StatusBadRequest)
			return
		}
		photo := Photo{
			ID:             r.Header.Get("X-Photo-ID"),
			SenderDeviceID: r.Header.Get("X-Sender-Device"),
			SenderName:     r.Header.Get("X-Sender-Name"),
			RecipientID:    r.Header.Get("X-Recipient"),
			Filename:       r.Header.Get("X-Filename"),
			MimeType:       r.Header.Get("Content-Type"),
			CapturedAt:     parseTime(r.Header.Get("X-Captured-At")),
			Size:           int64(len(body)),
		}
		stored, err := repo.AddPhoto(photo, body)
		if err != nil {
			http.Error(w, "could not store photo", http.StatusInternalServerError)
			return
		}
		writeJSON(w, http.StatusCreated, stored)
	})
	return withHeaders(withAuth(mux, accessToken))
}

func parseTime(value string) time.Time {
	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil {
		return time.Now().UTC()
	}
	return parsed.UTC()
}

func withAuth(next http.Handler, accessToken string) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if accessToken != "" && r.URL.Path != "/healthz" && r.URL.Path != "/connect" {
			provided := r.Header.Get("X-Locket-Token")
			if subtle.ConstantTimeCompare([]byte(provided), []byte(accessToken)) != 1 {
				http.Error(w, "unauthorized", http.StatusUnauthorized)
				return
			}
		}
		next.ServeHTTP(w, r)
	})
}

func withHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		if r.Method == http.MethodOptions {
			w.Header().Set("Access-Control-Allow-Origin", "*")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-Locket-Token, X-Sender-Device, X-Sender-Name, X-Recipient, X-Filename, X-Captured-At, X-Photo-ID")
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

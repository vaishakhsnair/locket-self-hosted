package store

import (
	"bufio"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestServerRequiresConfiguredAccessToken(t *testing.T) {
	repo, err := New(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(NewServer(repo, "secret"))
	defer server.Close()

	response, err := http.Get(server.URL + "/healthz")
	if err != nil || response.StatusCode != http.StatusOK {
		t.Fatalf("health check failed: err=%v status=%d", err, response.StatusCode)
	}
	response.Body.Close()

	response, err = http.Get(server.URL + "/v1/photos")
	if err != nil || response.StatusCode != http.StatusUnauthorized {
		t.Fatalf("unauthenticated request status=%d err=%v", response.StatusCode, err)
	}
	response.Body.Close()

	request, _ := http.NewRequest(http.MethodGet, server.URL+"/v1/photos", nil)
	request.Header.Set("X-Locket-Token", "secret")
	response, err = http.DefaultClient.Do(request)
	if err != nil || response.StatusCode != http.StatusOK {
		t.Fatalf("authenticated request status=%d err=%v", response.StatusCode, err)
	}
	response.Body.Close()
}

func TestServerPublishesPhotoEvents(t *testing.T) {
	repo, err := New(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(NewServer(repo, ""))
	defer server.Close()

	request, _ := http.NewRequest(http.MethodGet, server.URL+"/v1/events", nil)
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	reader := bufio.NewReader(response.Body)
	line, err := reader.ReadString('\n')
	if err != nil || !strings.Contains(line, "connected") {
		t.Fatalf("missing stream prelude: %q err=%v", line, err)
	}

	go func() {
		_, _ = repo.AddPhoto(Photo{ID: "event-1", CapturedAt: time.Now().UTC(), Filename: "event.jpg"}, []byte("media"))
	}()
	deadline := time.After(2 * time.Second)
	for {
		line, err = reader.ReadString('\n')
		if err != nil {
			t.Fatal(err)
		}
		if strings.HasPrefix(line, "data: ") {
			if !strings.Contains(line, "event-1") {
				t.Fatalf("unexpected event: %s", line)
			}
			break
		}
		select {
		case <-deadline:
			t.Fatal(fmt.Errorf("timed out waiting for event"))
		default:
		}
	}
}

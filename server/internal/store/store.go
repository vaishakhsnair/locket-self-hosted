package store

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

type Photo struct {
	ID             string    `json:"id"`
	SenderDeviceID string    `json:"senderDeviceId"`
	SenderName     string    `json:"senderName"`
	RecipientID    string    `json:"recipientId"`
	CapturedAt     time.Time `json:"capturedAt"`
	ReceivedAt     time.Time `json:"receivedAt"`
	Filename       string    `json:"filename"`
	MimeType       string    `json:"mimeType"`
	Size           int64     `json:"size"`
	Status         string    `json:"status"`
}

type Repository struct {
	mu          sync.RWMutex
	root        string
	photos      []Photo
	subscribers map[chan Photo]struct{}
}

func New(root string) (*Repository, error) {
	if err := os.MkdirAll(filepath.Join(root, "media"), 0o700); err != nil {
		return nil, err
	}
	r := &Repository{root: root, subscribers: make(map[chan Photo]struct{})}
	if bytes, err := os.ReadFile(filepath.Join(root, "photos.json")); err == nil {
		_ = json.Unmarshal(bytes, &r.photos)
	}
	return r, nil
}

func (r *Repository) AddPhoto(photo Photo, media []byte) (Photo, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if photo.ID == "" || strings.ContainsAny(photo.ID, "/\\") || strings.ContainsAny(photo.ID, "\x00") {
		photo.ID = newID()
	}
	if photo.ReceivedAt.IsZero() {
		photo.ReceivedAt = time.Now().UTC()
	}
	if photo.Status == "" {
		photo.Status = "received"
	}
	if err := os.WriteFile(filepath.Join(r.root, "media", photo.ID+".bin"), media, 0o600); err != nil {
		return Photo{}, err
	}
	r.photos = append(r.photos, photo)
	sort.SliceStable(r.photos, func(i, j int) bool { return r.photos[i].CapturedAt.After(r.photos[j].CapturedAt) })
	if err := r.persistLocked(); err != nil {
		return Photo{}, err
	}
	r.publishLocked(photo)
	return photo, nil
}

func (r *Repository) Subscribe() (<-chan Photo, func()) {
	r.mu.Lock()
	defer r.mu.Unlock()
	channel := make(chan Photo, 8)
	r.subscribers[channel] = struct{}{}
	return channel, func() {
		r.mu.Lock()
		if _, exists := r.subscribers[channel]; exists {
			delete(r.subscribers, channel)
			close(channel)
		}
		r.mu.Unlock()
	}
}

func (r *Repository) publishLocked(photo Photo) {
	for channel := range r.subscribers {
		select {
		case channel <- photo:
		default:
		}
	}
}

func (r *Repository) List(before, partner string, limit int) []Photo {
	r.mu.RLock()
	defer r.mu.RUnlock()
	if limit <= 0 || limit > 100 {
		limit = 40
	}
	result := make([]Photo, 0, limit)
	for _, photo := range r.photos {
		if partner != "" && photo.RecipientID != partner && photo.SenderDeviceID != partner {
			continue
		}
		if before != "" && photo.CapturedAt.UnixNano() >= parseCursor(before) {
			continue
		}
		result = append(result, photo)
		if len(result) == limit {
			break
		}
	}
	return result
}

func (r *Repository) Media(id string) ([]byte, error) {
	if id == "" || strings.Contains(id, "/") || strings.Contains(id, "\\") {
		return nil, errors.New("invalid photo id")
	}
	return os.ReadFile(filepath.Join(r.root, "media", id+".bin"))
}

func (r *Repository) persistLocked() error {
	bytes, err := json.MarshalIndent(r.photos, "", "  ")
	if err != nil {
		return err
	}
	tmp := filepath.Join(r.root, "photos.json.tmp")
	if err := os.WriteFile(tmp, bytes, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, filepath.Join(r.root, "photos.json"))
}

func parseCursor(value string) int64 {
	var cursor int64
	_, _ = fmt.Sscan(value, &cursor)
	return cursor
}

func newID() string {
	bytes := make([]byte, 12)
	_, _ = rand.Read(bytes)
	return hex.EncodeToString(bytes)
}

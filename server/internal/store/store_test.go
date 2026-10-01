package store

import (
	"os"
	"testing"
	"time"
)

func TestRepositoryStoresAndListsEncryptedBytes(t *testing.T) {
	root := t.TempDir()
	repo, err := New(root)
	if err != nil {
		t.Fatal(err)
	}
	captured := time.Now().UTC().Truncate(time.Second)
	photo := Photo{ID: "photo-1", CapturedAt: captured, Filename: "moment.jpg", MimeType: "application/octet-stream"}
	secret := []byte("ciphertext-not-a-photo")
	stored, err := repo.AddPhoto(photo, secret)
	if err != nil {
		t.Fatal(err)
	}
	if stored.ID != "photo-1" || stored.Status != "received" || stored.ReceivedAt.IsZero() {
		t.Fatalf("unexpected stored photo: %#v", stored)
	}
	listed := repo.List("", "", 40)
	if len(listed) != 1 || listed[0].ID != "photo-1" {
		t.Fatalf("unexpected list: %#v", listed)
	}
	read, err := repo.Media("photo-1")
	if err != nil {
		t.Fatal(err)
	}
	if string(read) != string(secret) {
		t.Fatalf("media changed: %q", read)
	}
	if _, err := os.Stat(root + "/photos.json"); err != nil {
		t.Fatal(err)
	}
}

func TestRepositoryReplacesUnsafeClientID(t *testing.T) {
	root := t.TempDir()
	repo, err := New(root)
	if err != nil {
		t.Fatal(err)
	}
	stored, err := repo.AddPhoto(Photo{ID: "../outside", CapturedAt: time.Now().UTC()}, []byte("x"))
	if err != nil {
		t.Fatal(err)
	}
	if stored.ID == "../outside" || stored.ID == "" {
		t.Fatalf("unsafe ID was retained: %q", stored.ID)
	}
	if _, err := os.Stat(root + "/media/" + stored.ID + ".bin"); err != nil {
		t.Fatal(err)
	}
}

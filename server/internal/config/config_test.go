package config

import (
	"io"
	"log/slog"
	"testing"
)

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

func TestDefaultIsValidExceptToken(t *testing.T) {
	cfg := Default()
	if cfg.Version != CurrentVersion {
		t.Errorf("Version = %d, quero %d", cfg.Version, CurrentVersion)
	}
	if cfg.Port != DefaultPort {
		t.Errorf("Port = %d, quero %d", cfg.Port, DefaultPort)
	}
	if cfg.Addr() != "0.0.0.0:5050" {
		t.Errorf("Addr() = %q", cfg.Addr())
	}
	// Sem token, a validação precisa recusar — é o Store que gera um.
	if err := cfg.validate(); err == nil {
		t.Error("validate() sem token deveria falhar")
	}
}

func TestCloneIsDeep(t *testing.T) {
	orig := Config{
		Apps: []App{{ID: "a", Name: "App", Path: "p", Args: []string{"--flag"}}},
	}
	cp := orig.clone()
	cp.Apps[0].Name = "Alterado"
	cp.Apps[0].Args[0] = "--outro"

	if orig.Apps[0].Name != "App" {
		t.Error("clone compartilhou o slice de apps")
	}
	if orig.Apps[0].Args[0] != "--flag" {
		t.Error("clone compartilhou o slice de args")
	}
}

func TestIndexOf(t *testing.T) {
	cfg := Config{Apps: []App{{ID: "um"}, {ID: "dois"}}}
	if got := cfg.indexOf("dois"); got != 1 {
		t.Errorf("indexOf(dois) = %d, quero 1", got)
	}
	if got := cfg.indexOf("tres"); got != -1 {
		t.Errorf("indexOf(tres) = %d, quero -1", got)
	}
}

func TestNewIDIsUniqueAndOpaque(t *testing.T) {
	const n = 100_000
	seen := make(map[string]bool, n)
	for i := 0; i < n; i++ {
		id, err := NewID()
		if err != nil {
			t.Fatalf("NewID: %v", err)
		}
		if len(id) != idBytes*2 {
			t.Fatalf("len(id) = %d, quero %d", len(id), idBytes*2)
		}
		if seen[id] {
			t.Fatalf("colisão de id em %d iterações: %s", i, id)
		}
		seen[id] = true
	}
}

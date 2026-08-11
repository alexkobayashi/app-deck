package config

import (
	"errors"
	"io/fs"
	"os"
	"testing"
	"time"
)

// fakeInfo é o mínimo de fs.FileInfo necessário para o CheckPaths.
type fakeInfo struct {
	name string
	dir  bool
}

func (f fakeInfo) Name() string       { return f.name }
func (f fakeInfo) Size() int64        { return 0 }
func (f fakeInfo) Mode() fs.FileMode  { return 0 }
func (f fakeInfo) ModTime() time.Time { return time.Time{} }
func (f fakeInfo) IsDir() bool        { return f.dir }
func (f fakeInfo) Sys() any           { return nil }

// A validação de caminhos precisa ser testável em qualquer sistema — daí o
// stat injetável, que aqui simula caminhos do Windows a partir do Linux.
func TestCheckPaths(t *testing.T) {
	cfg := Config{Apps: []App{
		{ID: "existe", Path: `C:\Windows\System32\calc.exe`},
		{ID: "faltando", Path: `C:\Program Files\Sumiu\sumiu.exe`},
		{ID: "diretorio", Path: `C:\Program Files\Pasta`},
		{ID: "erro", Path: `\\servidor-offline\share\app.exe`},
	}}

	stat := func(path string) (fs.FileInfo, error) {
		switch path {
		case `C:\Windows\System32\calc.exe`:
			return fakeInfo{name: "calc.exe"}, nil
		case `C:\Program Files\Pasta`:
			return fakeInfo{name: "Pasta", dir: true}, nil
		case `\\servidor-offline\share\app.exe`:
			return nil, errors.New("host inacessível")
		default:
			return nil, fs.ErrNotExist
		}
	}

	warns := CheckPaths(cfg, stat)
	if len(warns) != 3 {
		t.Fatalf("quero 3 avisos, tenho %d: %v", len(warns), warns)
	}

	byID := map[string]Warning{}
	for _, w := range warns {
		byID[w.AppID] = w
	}
	for _, id := range []string{"faltando", "diretorio", "erro"} {
		if _, ok := byID[id]; !ok {
			t.Errorf("faltou aviso para o app %q", id)
		}
	}
	if _, ok := byID["existe"]; ok {
		t.Error("um executável existente não deveria gerar aviso")
	}
}

// Caminho quebrado é aviso, nunca erro: o servidor tem que subir mesmo com
// atalhos apontando para programas desinstalados.
func TestOpenWarnsButSucceedsWithBrokenPaths(t *testing.T) {
	store := newTestStore(t)
	if _, err := store.AddApp("Sumiu", `C:\nao\existe\mesmo.exe`, nil); err != nil {
		t.Fatal(err)
	}

	reopened, warns, err := Open(store.Path(), testLogger())
	if err != nil {
		t.Fatalf("Open falhou por causa de um path quebrado: %v", err)
	}
	if len(reopened.Apps()) != 1 {
		t.Error("o atalho com path quebrado foi descartado")
	}
	if !hasWarning(warns, "path") {
		t.Errorf("esperava aviso de path, avisos: %v", warns)
	}
}

func TestCheckPathsWithRealStat(t *testing.T) {
	dir := t.TempDir()
	existing, err := os.CreateTemp(dir, "prog-*")
	if err != nil {
		t.Fatal(err)
	}
	existing.Close()

	cfg := Config{Apps: []App{
		{ID: "ok", Path: existing.Name()},
		{ID: "nao", Path: dir + string(os.PathSeparator) + "inexistente"},
	}}
	warns := CheckPaths(cfg, os.Stat)
	if len(warns) != 1 || warns[0].AppID != "nao" {
		t.Errorf("avisos = %v", warns)
	}
}

func TestValidateRejectsIncompleteApps(t *testing.T) {
	base := func() Config {
		return Config{
			Version: CurrentVersion,
			Token:   "um-token-longo-o-suficiente",
			Port:    DefaultPort,
			Bind:    DefaultBind,
		}
	}

	tests := []struct {
		name string
		apps []App
	}{
		{"sem id", []App{{Name: "A", Path: "p"}}},
		{"sem name", []App{{ID: "x", Path: "p"}}},
		{"sem path", []App{{ID: "x", Name: "A"}}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			cfg := base()
			cfg.Apps = tc.apps
			if err := cfg.validate(); !errors.Is(err, ErrInvalid) {
				t.Errorf("erro = %v, quero ErrInvalid", err)
			}
		})
	}
}

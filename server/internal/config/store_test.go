package config

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"

	"github.com/alexkobayashi/app-deck/server/internal/token"
)

func newTestStore(t *testing.T) *Store {
	t.Helper()
	path := filepath.Join(t.TempDir(), FileName)
	store, _, err := Open(path, testLogger())
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	return store
}

// readBack lê o arquivo direto do disco: é a única forma de provar que o
// estado em memória e o persistido não divergiram.
func readBack(t *testing.T, store *Store) Config {
	t.Helper()
	raw, err := os.ReadFile(store.Path())
	if err != nil {
		t.Fatalf("ler config: %v", err)
	}
	var cfg Config
	if err := json.Unmarshal(raw, &cfg); err != nil {
		t.Fatalf("config em disco inválido: %v", err)
	}
	return cfg
}

func TestOpenCreatesConfigWithStrongToken(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "sub", FileName)

	store, warns, err := Open(path, testLogger())
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	if len(warns) == 0 {
		t.Error("criar o config do zero deveria avisar o usuário")
	}
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("config.json não foi criado: %v", err)
	}

	tok := store.Token()
	if len(tok) < 32 {
		t.Errorf("token gerado tem %d caracteres, esperava pelo menos 32", len(tok))
	}
	if token.Weak(tok) {
		t.Error("o token gerado foi classificado como fraco")
	}
	if got := readBack(t, store); got.Token != tok {
		t.Error("o token em disco difere do token em memória")
	}
}

func TestOpenGeneratesTokenWhenEmpty(t *testing.T) {
	path := filepath.Join(t.TempDir(), FileName)
	if err := os.WriteFile(path, []byte(`{"version":2,"token":"","port":5050,"apps":[]}`), 0o600); err != nil {
		t.Fatal(err)
	}

	store, warns, err := Open(path, testLogger())
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	if store.Token() == "" {
		t.Fatal("token continuou vazio")
	}
	if !hasWarning(warns, "token") {
		t.Errorf("esperava aviso sobre o token gerado, avisos: %v", warns)
	}
}

func TestOpenFillsMissingIDs(t *testing.T) {
	path := filepath.Join(t.TempDir(), FileName)
	raw := `{"version":2,"token":"um-token-longo-o-suficiente","port":5050,"apps":[
		{"name":"Sem id","path":"C:\\a.exe"},
		{"id":"dup","name":"A","path":"C:\\b.exe"},
		{"id":"dup","name":"B","path":"C:\\c.exe"}
	]}`
	if err := os.WriteFile(path, []byte(raw), 0o600); err != nil {
		t.Fatal(err)
	}

	store, warns, err := Open(path, testLogger())
	if err != nil {
		t.Fatalf("Open: %v", err)
	}

	apps := store.Apps()
	seen := map[string]bool{}
	for _, a := range apps {
		if a.ID == "" {
			t.Errorf("app %q ficou sem id", a.Name)
		}
		if seen[a.ID] {
			t.Errorf("id duplicado sobreviveu: %s", a.ID)
		}
		seen[a.ID] = true
	}
	if !hasWarning(warns, "id") {
		t.Errorf("id duplicado deveria gerar aviso, avisos: %v", warns)
	}
	if got := readBack(t, store); len(got.Apps) != 3 {
		t.Errorf("apps em disco = %d, quero 3", len(got.Apps))
	}
}

func TestOpenRejectsInvalidJSON(t *testing.T) {
	path := filepath.Join(t.TempDir(), FileName)
	if err := os.WriteFile(path, []byte(`{"version":2,`), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, _, err := Open(path, testLogger()); err == nil {
		t.Fatal("esperava erro em config.json malformado")
	}
}

func TestOpenRejectsInvalidPort(t *testing.T) {
	path := filepath.Join(t.TempDir(), FileName)
	raw := `{"version":2,"token":"um-token-longo-o-suficiente","port":99999,"apps":[]}`
	if err := os.WriteFile(path, []byte(raw), 0o600); err != nil {
		t.Fatal(err)
	}
	_, _, err := Open(path, testLogger())
	if !errors.Is(err, ErrInvalid) {
		t.Fatalf("erro = %v, quero ErrInvalid", err)
	}
}

func TestAddAppPersists(t *testing.T) {
	store := newTestStore(t)

	app, err := store.AddApp("  Calculadora  ", "  C:\\Windows\\System32\\calc.exe  ", nil)
	if err != nil {
		t.Fatalf("AddApp: %v", err)
	}
	if app.Name != "Calculadora" || app.Path != "C:\\Windows\\System32\\calc.exe" {
		t.Errorf("espaços não foram removidos: %+v", app)
	}
	if app.ID == "" {
		t.Error("app criado sem id")
	}

	onDisk := readBack(t, store)
	if len(onDisk.Apps) != 1 || onDisk.Apps[0].ID != app.ID {
		t.Errorf("app não foi persistido: %+v", onDisk.Apps)
	}
}

func TestAddAppRejectsEmptyFields(t *testing.T) {
	store := newTestStore(t)

	if _, err := store.AddApp("", "C:\\a.exe", nil); !errors.Is(err, ErrInvalid) {
		t.Errorf("name vazio: erro = %v, quero ErrInvalid", err)
	}
	if _, err := store.AddApp("Nome", "   ", nil); !errors.Is(err, ErrInvalid) {
		t.Errorf("path vazio: erro = %v, quero ErrInvalid", err)
	}
	if len(store.Apps()) != 0 {
		t.Error("uma criação inválida não deve alterar o estado")
	}
}

func TestUpdateAppPartial(t *testing.T) {
	store := newTestStore(t)
	app, err := store.AddApp("Antigo", "C:\\antigo.exe", []string{"--x"})
	if err != nil {
		t.Fatal(err)
	}

	novo := "Novo"
	updated, err := store.UpdateApp(app.ID, AppUpdate{Name: &novo})
	if err != nil {
		t.Fatalf("UpdateApp: %v", err)
	}
	if updated.Name != "Novo" {
		t.Errorf("Name = %q", updated.Name)
	}
	if updated.Path != "C:\\antigo.exe" {
		t.Errorf("Path mudou sem ser pedido: %q", updated.Path)
	}
	if len(updated.Args) != 1 || updated.Args[0] != "--x" {
		t.Errorf("Args mudou sem ser pedido: %v", updated.Args)
	}
	if updated.ID != app.ID {
		t.Error("o id mudou numa edição")
	}

	vazio := ""
	if _, err := store.UpdateApp(app.ID, AppUpdate{Path: &vazio}); !errors.Is(err, ErrInvalid) {
		t.Errorf("path vazio: erro = %v, quero ErrInvalid", err)
	}
	if _, err := store.UpdateApp("nao-existe", AppUpdate{Name: &novo}); !errors.Is(err, ErrNotFound) {
		t.Errorf("id inexistente: erro = %v, quero ErrNotFound", err)
	}

	if got := readBack(t, store); got.Apps[0].Name != "Novo" {
		t.Errorf("edição não foi persistida: %+v", got.Apps[0])
	}
}

func TestUpdateAppClearsArgsWithEmptyList(t *testing.T) {
	store := newTestStore(t)
	app, err := store.AddApp("App", "C:\\a.exe", []string{"--x"})
	if err != nil {
		t.Fatal(err)
	}

	empty := []string{}
	updated, err := store.UpdateApp(app.ID, AppUpdate{Args: &empty})
	if err != nil {
		t.Fatal(err)
	}
	if len(updated.Args) != 0 {
		t.Errorf("Args = %v, quero vazio", updated.Args)
	}
	// args omitempty: uma lista vazia não deve poluir o arquivo.
	raw, err := os.ReadFile(store.Path())
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(raw), "args") {
		t.Errorf("args vazio foi gravado no arquivo: %s", raw)
	}
}

func TestDeleteApp(t *testing.T) {
	store := newTestStore(t)
	a, _ := store.AddApp("A", "C:\\a.exe", nil)
	b, _ := store.AddApp("B", "C:\\b.exe", nil)

	if err := store.DeleteApp(a.ID); err != nil {
		t.Fatalf("DeleteApp: %v", err)
	}
	apps := store.Apps()
	if len(apps) != 1 || apps[0].ID != b.ID {
		t.Errorf("estado após remoção: %+v", apps)
	}
	if got := readBack(t, store); len(got.Apps) != 1 || got.Apps[0].ID != b.ID {
		t.Errorf("remoção não foi persistida: %+v", got.Apps)
	}
	if err := store.DeleteApp(a.ID); !errors.Is(err, ErrNotFound) {
		t.Errorf("remover duas vezes: erro = %v, quero ErrNotFound", err)
	}
}

func TestSnapshotIsIsolated(t *testing.T) {
	store := newTestStore(t)
	if _, err := store.AddApp("A", "C:\\a.exe", []string{"--x"}); err != nil {
		t.Fatal(err)
	}

	snap := store.Snapshot()
	snap.Apps[0].Name = "Mexido"
	snap.Apps[0].Args[0] = "--mexido"
	snap.Token = "mexido"

	if store.Apps()[0].Name != "A" || store.Apps()[0].Args[0] != "--x" {
		t.Error("mutar o Snapshot afetou o Store")
	}
	if store.Token() == "mexido" {
		t.Error("mutar o Snapshot afetou o token")
	}
}

// Rodar com -race: garante que as mutações concorrentes são seguras e que
// o arquivo final é consistente com o estado em memória.
func TestStoreConcurrentMutations(t *testing.T) {
	store := newTestStore(t)

	const n = 50
	var wg sync.WaitGroup
	ids := make(chan string, n)

	for i := 0; i < n; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			app, err := store.AddApp("App", "C:\\app.exe", nil)
			if err != nil {
				t.Errorf("AddApp: %v", err)
				return
			}
			ids <- app.ID
		}(i)
	}
	// Leituras concorrentes às escritas.
	for i := 0; i < n; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_ = store.Snapshot()
			_ = store.Apps()
			_ = store.Token()
		}()
	}
	wg.Wait()
	close(ids)

	if got := len(store.Apps()); got != n {
		t.Fatalf("apps em memória = %d, quero %d", got, n)
	}
	if got := len(readBack(t, store).Apps); got != n {
		t.Fatalf("apps em disco = %d, quero %d", got, n)
	}

	// Metade é removida em paralelo.
	var created []string
	for id := range ids {
		created = append(created, id)
	}
	toRemove := created[:n/2]

	var wg2 sync.WaitGroup
	for _, id := range toRemove {
		wg2.Add(1)
		go func(id string) {
			defer wg2.Done()
			if err := store.DeleteApp(id); err != nil {
				t.Errorf("DeleteApp: %v", err)
			}
		}(id)
	}
	wg2.Wait()

	want := n - len(toRemove)
	if got := len(store.Apps()); got != want {
		t.Errorf("apps em memória = %d, quero %d", got, want)
	}
	if got := len(readBack(t, store).Apps); got != want {
		t.Errorf("apps em disco = %d, quero %d", got, want)
	}
}
